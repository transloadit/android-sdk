package com.transloadit.android.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.transloadit.sdk.Assembly;
import com.transloadit.sdk.SignatureProvider;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RemoteSigningStatusTest {
    private static final String ASSEMBLY_ID = "0123456789abcdef0123456789abcdef";

    @Test
    public void relativeStatusUrlWorksWithoutSigning() throws Exception {
        try (MockWebServer api = new MockWebServer()) {
            api.start();
            String path = "/assemblies/" + ASSEMBLY_ID;
            api.enqueue(assemblyResponse("ASSEMBLY_COMPLETED", api.url(path).toString()));
            SignatureProvider provider = paramsJson -> {
                throw new RequestException("Status requests must not ask for a signature");
            };
            AndroidTransloadit transloadit = new AndroidTransloadit("key", provider,
                    api.url("/").toString().replaceAll("/$", ""));

            assertTrue(transloadit.getAssemblyByUrl(path).isCompleted());
            RecordedRequest request = api.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("GET", request.getMethod());
            assertEquals(path, request.getPath());
        }
    }

    @Test
    public void pollingWorksWithCreationOnlySignatureProvider() throws Exception {
        try (MockWebServer api = new MockWebServer(); MockWebServer status = new MockWebServer()) {
            api.start();
            status.start();
            String statusUrl = status.url("/status/" + ASSEMBLY_ID).toString();
            api.enqueue(assemblyResponse("ASSEMBLY_EXECUTING", statusUrl));
            status.enqueue(assemblyResponse("ASSEMBLY_EXECUTING", statusUrl));
            status.enqueue(assemblyResponse("ASSEMBLY_COMPLETED", statusUrl));

            AtomicInteger signingCalls = new AtomicInteger();
            SignatureProvider provider = paramsJson -> {
                signingCalls.incrementAndGet();
                if (!new JSONObject(paramsJson).has("template_id")) {
                    throw new RequestException("Only Assembly creation with a template_id can be signed");
                }
                return "sha384:test-signature";
            };
            AndroidTransloadit transloadit = new AndroidTransloadit("key", provider,
                    api.url("/").toString().replaceAll("/$", ""));
            Assembly assembly = transloadit.newAssembly();
            assembly.addOption("template_id", "template");
            assembly.setShouldWaitForCompletion(true);

            AssemblyResponse response = assembly.save(false);

            assertTrue(response.isCompleted());
            assertEquals(1, signingCalls.get());
            assertEquals(provider, transloadit.getSignatureProvider());
            assertTrue(transloadit.isSigningEnabledForTesting());
            assertEquals(1, api.getRequestCount());
            assertEquals(2, status.getRequestCount());
            for (int i = 0; i < 2; i++) {
                RecordedRequest request = status.takeRequest(1, TimeUnit.SECONDS);
                assertNotNull(request);
                assertEquals("GET", request.getMethod());
                assertEquals("/status/" + ASSEMBLY_ID, request.getPath());
                assertTrue(request.getHeader("Transloadit-Client").contains("android-sdk:"));
            }
        }
    }

    @Test
    public void workerWaitsForCompletionWithCreationOnlySigner() throws Exception {
        assertWorkerResult("ASSEMBLY_EXECUTING");
    }

    @Test
    public void workerReturnsAlreadyCompletedAssembly() throws Exception {
        assertWorkerResult("ASSEMBLY_COMPLETED");
    }

    @Test
    public void workerFailsForAbortedInitialAssembly() throws Exception {
        assertWorkerResult("REQUEST_ABORTED");
    }

    @Test
    public void workerFailsForCanceledInitialAssembly() throws Exception {
        assertWorkerResult("ASSEMBLY_CANCELED");
    }

    @Test
    public void workerFailsForInitialApiError() throws Exception {
        assertWorkerResult(null);
    }

    private void assertWorkerResult(String initialState) throws Exception {
        try (MockWebServer api = new MockWebServer(); MockWebServer status = new MockWebServer()) {
            api.start();
            status.start();
            String statusUrl = status.url("/status/" + ASSEMBLY_ID).toString();
            AtomicInteger signingCalls = new AtomicInteger();
            api.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    try {
                        if ("/sign".equals(request.getPath())) {
                            signingCalls.incrementAndGet();
                            if (!new JSONObject(request.getBody().readUtf8()).has("template_id")) {
                                return new MockResponse().setResponseCode(400)
                                        .setBody("Only Assembly creation with a template_id can be signed");
                            }
                            return new MockResponse().setHeader("Content-Type", "application/json")
                                    .setBody("{\"signature\":\"sha384:test-signature\"}");
                        }
                        if ("POST".equals(request.getMethod()) && request.getPath().startsWith("/assemblies")) {
                            return new MockResponse().setHeader("Content-Type", "application/json")
                                    .setBody(new JSONObject(assemblyJson(initialState, statusUrl))
                                            .put("update_stream_url", api.url("/events").toString()).toString());
                        }
                        if ("/events".equals(request.getPath())) {
                            return new MockResponse().setHeader("Content-Type", "text/event-stream")
                                    .setBody("data: assembly_finished\n\n");
                        }
                        return new MockResponse().setResponseCode(404);
                    } catch (JSONException e) {
                        throw new AssertionError(e);
                    }
                }
            });
            status.enqueue(assemblyResponse("ASSEMBLY_COMPLETED", statusUrl));

            AndroidAssemblyWorkConfig config = AndroidAssemblyWorkConfig.newBuilder("key")
                    .signatureProvider(api.url("/sign").toString())
                    .hostUrl(api.url("/").toString().replaceAll("/$", ""))
                    .paramsJson("{\"template_id\":\"template\"}")
                    .resumable(false)
                    .waitForCompletion(true)
                    .completionTimeoutMillis(5_000)
                    .build();
            AndroidAssemblyUploadWorker worker = TestListenableWorkerBuilder
                    .from(ApplicationProvider.getApplicationContext(), AndroidAssemblyUploadWorker.class)
                    .setInputData(config.toInputData())
                    .build();

            ListenableWorker.Result result = worker.startWork().get(10, TimeUnit.SECONDS);

            boolean needsStatus = "ASSEMBLY_EXECUTING".equals(initialState);
            boolean successful = needsStatus || "ASSEMBLY_COMPLETED".equals(initialState);
            String message = "Unexpected work result: " + result + " (API requests: " + api.getRequestCount()
                    + ", signing calls: " + signingCalls.get() + ")";
            if (successful) {
                assertTrue(message, result instanceof ListenableWorker.Result.Success);
            } else {
                assertTrue(message, result instanceof ListenableWorker.Result.Failure);
                assertEquals(initialState == null ? "INVALID_TEMPLATE" : initialState,
                        result.getOutputData().getString("error"));
            }
            if (initialState == null) {
                assertNull(result.getOutputData().getString(AndroidAssemblyUploadWorker.OUTPUT_ASSEMBLY_ID));
                assertNull(result.getOutputData().getString(AndroidAssemblyUploadWorker.OUTPUT_ASSEMBLY_URL));
                assertNull(result.getOutputData().getString(AndroidAssemblyUploadWorker.OUTPUT_SSL_URL));
            } else {
                assertEquals(ASSEMBLY_ID, result.getOutputData().getString(AndroidAssemblyUploadWorker.OUTPUT_ASSEMBLY_ID));
                assertEquals("http://unused.example/assembly",
                        result.getOutputData().getString(AndroidAssemblyUploadWorker.OUTPUT_ASSEMBLY_URL));
                assertEquals(statusUrl, result.getOutputData().getString(AndroidAssemblyUploadWorker.OUTPUT_SSL_URL));
            }
            assertEquals(1, signingCalls.get());
            assertEquals(needsStatus ? 3 : 2, api.getRequestCount());
            assertEquals(needsStatus ? 1 : 0, status.getRequestCount());
            if (needsStatus) {
                assertEquals("/status/" + ASSEMBLY_ID, status.takeRequest(1, TimeUnit.SECONDS).getPath());
            }
        }
    }

    private static MockResponse assemblyResponse(String state, String statusUrl) throws JSONException {
        return new MockResponse().setHeader("Content-Type", "application/json")
                .setBody(assemblyJson(state, statusUrl));
    }

    private static String assemblyJson(String state, String statusUrl) throws JSONException {
        if (state == null) {
            return new JSONObject().put("error", "INVALID_TEMPLATE").toString();
        }
        JSONObject json = new JSONObject().put("assembly_id", ASSEMBLY_ID)
                .put("assembly_url", "http://unused.example/assembly")
                .put("assembly_ssl_url", statusUrl);
        json.put("ok", state);
        return json.toString();
    }
}
