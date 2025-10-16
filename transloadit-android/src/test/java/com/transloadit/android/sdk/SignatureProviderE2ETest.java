package com.transloadit.android.sdk;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.transloadit.sdk.SignatureProvider;
import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Host-side end-to-end verification for the signature provider flow.
 *
 * <p>The test executes only when ANDROID_SDK_E2E=true and Transloadit credentials are
 * provided via environment variables. Otherwise it is skipped so PR runs remain fast.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28})
public class SignatureProviderE2ETest {
    private static final String ENV_E2E_FLAG = "ANDROID_SDK_E2E";
    private static final String ENV_KEY = "TRANSLOADIT_KEY";
    private static final String ENV_SECRET = "TRANSLOADIT_SECRET";
    private static final String SIGNATURE_ENDPOINT = "/sign";

    private static boolean e2eEnabled;
    private static String transloaditKey;
    private static String transloaditSecret;

    @BeforeClass
    public static void loadEnv() {
        e2eEnabled = parseBoolean(System.getenv(ENV_E2E_FLAG));
        transloaditKey = firstNonEmpty(System.getenv(ENV_KEY));
        transloaditSecret = firstNonEmpty(System.getenv(ENV_SECRET));
    }

    @Test
    public void uploadCompletesViaExternalSignatureProviderWithPauseResume() throws Exception {
        Assume.assumeTrue("E2E signature-provider test disabled", e2eEnabled);
        Assume.assumeTrue("TRANSLOADIT_KEY missing", !isNullOrEmpty(transloaditKey));
        Assume.assumeTrue("TRANSLOADIT_SECRET missing", !isNullOrEmpty(transloaditSecret));

        Context context = ApplicationProvider.getApplicationContext();
        File upload = createTempUpload(context, 2 * 1024 * 1024); // 2 MiB to ensure pause window

        AtomicBoolean progressObserved = new AtomicBoolean(false);
        AtomicBoolean uploadFinished = new AtomicBoolean(false);
        AtomicBoolean sseObserved = new AtomicBoolean(false);
        AtomicBoolean pauseInvoked = new AtomicBoolean(false);
        AtomicBoolean resumeInvoked = new AtomicBoolean(false);

        List<String> timeline = Collections.synchronizedList(new ArrayList<>());
        long startMillis = System.currentTimeMillis();
        Consumer<String> log = message -> {
            long delta = System.currentTimeMillis() - startMillis;
            String entry = String.format(Locale.US, "[+%6dms] %s", delta, message);
            timeline.add(entry);
            System.out.println("[SignatureProviderE2ETest] " + entry);
        };

        CountDownLatch progressLatch = new CountDownLatch(1);
        CountDownLatch sseLatch = new CountDownLatch(1);

        log.accept("E2E flag=" + e2eEnabled + " key present=" + !isNullOrEmpty(transloaditKey));
        log.accept("Temp upload path=" + upload.getAbsolutePath() + " size=" + upload.length());

        try (MockWebServer signingServer = startSigningServer(transloaditSecret)) {
            log.accept("Signing server url=" + signingServer.url(SIGNATURE_ENDPOINT));
            SignatureProvider provider = paramsJson ->
                    requestSignature(signingServer.url(SIGNATURE_ENDPOINT).url(), paramsJson);

            AndroidAssemblyListener listener = new AndroidAssemblyListener() {
                @Override
                public void onUploadFinished() {
                    uploadFinished.set(true);
                    log.accept("Upload finished callback");
                }

                @Override
                public void onUploadProgress(long uploadedBytes, long totalBytes) {
                    if (totalBytes > 0L && uploadedBytes > 0L) {
                        progressObserved.set(true);
                        progressLatch.countDown();
                        log.accept(String.format(Locale.US, "Upload progress %.2f%%", 100.0 * uploadedBytes / totalBytes));
                    }
                }

                @Override
                public void onUploadFailed(Exception exception) {
                    throw new AssertionError("Upload failed", exception);
                }

                @Override
                public void onAssemblyStatusUpdateFailed(Exception exception) {
                    throw new AssertionError("Status update failed", exception);
                }

                @Override
                public void onAssemblyProgress(JSONObject progressPerOriginalFile) {
                    sseObserved.set(true);
                    sseLatch.countDown();
                    log.accept("Assembly progress SSE: " + progressPerOriginalFile);
                }

                @Override
                public void onAssemblyResultFinished(JSONArray result) {
                    sseObserved.set(true);
                    sseLatch.countDown();
                    log.accept("Assembly result SSE count=" + result.length());
                }
            };

            AndroidTransloadit transloadit = new AndroidTransloadit(transloaditKey, provider);

            try (AndroidAssembly assembly = transloadit.newAssembly(listener, context)) {
                assembly.addFile(upload, "image");

                Map<String, Object> resize = new HashMap<>();
                resize.put("width", 32);
                resize.put("height", 32);
                resize.put("resize_strategy", "fit");
                resize.put("format", "jpg");
                assembly.addStep("resize", "/image/resize", resize);

                Future<AssemblyResponse> future = assembly.saveAsync(true);

                // Wait until some bytes are uploaded before pausing
                boolean progressSeen = progressLatch.await(2, TimeUnit.MINUTES);
                if (!progressSeen) {
                    log.accept("Timed out waiting for upload progress");
                }
                assertTrue("Upload progress not observed" + formatTimeline(timeline),
                        progressSeen);

                assembly.pauseUploads();
                pauseInvoked.set(true);
                log.accept("Uploads paused");

                Thread.sleep(TimeUnit.SECONDS.toMillis(2));

                assembly.resumeUploads();
                resumeInvoked.set(true);
                log.accept("Uploads resumed");

                AssemblyResponse initial = await(future, 5, TimeUnit.MINUTES);
                assertNotNull("Initial assembly response missing", initial);
                assertNotNull("Assembly ID missing", initial.getId());
                log.accept("Initial assembly id=" + initial.getId());

                AssemblyResponse completed = waitForCompletion(transloadit, initial.getId());
                assertNotNull("Final assembly response missing", completed);
                log.accept("Completed assembly ok=" + completed.json().optString("ok"));

                boolean sseSeen = sseLatch.await(2, TimeUnit.MINUTES);
                if (!sseSeen) {
                    log.accept("Timed out waiting for SSE events");
                    log.accept("Final assembly payload=" + completed.json());
                }
                assertTrue("SSE progress not observed" + formatTimeline(timeline),
                        sseSeen);

                JSONObject json = completed.json();
                assertTrue("Assembly not completed",
                        json.optString("ok", "").toUpperCase().contains("ASSEMBLY_COMPLETED"));

                JSONArray results = completed.getStepResult("resize");
                assertTrue("Resize step missing", results != null && results.length() > 0);
            }
        } finally {
            if (upload.exists()) {
                //noinspection ResultOfMethodCallIgnored
                upload.delete();
            }
        }

        assertTrue("Progress callback not observed", progressObserved.get());
        assertTrue("Upload finished callback not observed", uploadFinished.get());
        assertTrue("Pause not invoked", pauseInvoked.get());
        assertTrue("Resume not invoked", resumeInvoked.get());
        assertTrue("SSE events not observed" + formatTimeline(timeline), sseObserved.get());
    }

    private static MockWebServer startSigningServer(String secret) throws IOException {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (SIGNATURE_ENDPOINT.equals(request.getPath())
                        && "POST".equals(request.getMethod())) {
                    try {
                        String paramsJson = request.getBody().readUtf8();
                        String signature = computeSignature(paramsJson, secret);
                        JSONObject payload = new JSONObject();
                        payload.put("signature", signature);
                        return new MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(payload.toString());
                    } catch (JSONException e) {
                        return new MockResponse().setResponseCode(500)
                                .setBody("{\"error\":\"json construction failed\"}");
                    } catch (Exception e) {
                        return new MockResponse().setResponseCode(500)
                                .setBody("{\"error\":\"" + e.getMessage() + "\"}");
                    }
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        return server;
    }

    private static String requestSignature(URL endpoint, String paramsJson) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        try (OutputStream os = new BufferedOutputStream(connection.getOutputStream())) {
            os.write(paramsJson.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Signing server returned " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } finally {
            connection.disconnect();
        }

        try {
            JSONObject json = new JSONObject(response.toString());
            return json.getString("signature");
        } catch (JSONException e) {
            throw new IOException("Malformed signing response", e);
        }
    }

    private static AssemblyResponse waitForCompletion(AndroidTransloadit transloadit, String id)
            throws InterruptedException, LocalOperationException, RequestException {
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
        AssemblyResponse response = null;
        while (System.currentTimeMillis() < deadline) {
            response = transloadit.getAssembly(id);
            JSONObject json = response.json();
            String status = json.optString("ok", "");
            if (!isNullOrEmpty(status)
                    && status.toUpperCase().contains("ASSEMBLY_COMPLETED")) {
                return response;
            }
            Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        }
        return response;
    }

    private static <T> T await(Future<T> future, long timeout, TimeUnit unit)
            throws Exception {
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException timeoutException) {
            future.cancel(true);
            throw timeoutException;
        }
    }

    private static String computeSignature(String paramsJson, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA384");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA384"));
        byte[] digest = mac.doFinal(paramsJson.getBytes(StandardCharsets.UTF_8));
        return "sha384:" + toHex(digest);
    }

    private static String toHex(byte[] bytes) {
        char[] hexArray = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static File createTempUpload(Context context, int sizeBytes) throws IOException {
        File file = File.createTempFile("transloadit-e2e", ".bin", context.getCacheDir());
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStream os = new BufferedOutputStream(fos)) {
            byte[] buffer = new byte[8192];
            int written = 0;
            while (written < sizeBytes) {
                int toWrite = Math.min(buffer.length, sizeBytes - written);
                os.write(buffer, 0, toWrite);
                written += toWrite;
            }
        }
        return file;
    }

    private static boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static String firstNonEmpty(String value) {
        return isNullOrEmpty(value) ? null : value;
    }

    private static String formatTimeline(List<String> timeline) {
        if (timeline == null || timeline.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\nTimeline:");
        for (String entry : timeline) {
            sb.append("\n  ").append(entry);
        }
        return sb.toString();
    }
}
