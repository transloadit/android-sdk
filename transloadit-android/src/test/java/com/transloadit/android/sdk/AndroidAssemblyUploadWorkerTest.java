package com.transloadit.android.sdk;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AndroidAssemblyUploadWorkerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void missingFileFailsFast() throws Exception {
        File missing = new File(temporaryFolder.getRoot(), "does_not_exist.bin");
        AndroidAssemblyWorkConfig config = AndroidAssemblyWorkConfig.newBuilder("key", "secret")
                .paramsJson("{\"steps\":{\"noop\":{\"robot\":\"/video/encode\"}}}")
                .addFile(missing, "file")
                .build();

        Context context = ApplicationProvider.getApplicationContext();
        AndroidAssemblyUploadWorker worker = TestListenableWorkerBuilder
                .from(context, AndroidAssemblyUploadWorker.class)
                .setInputData(config.toInputData())
                .build();

        ListenableWorker.Result result = worker.startWork().get();
        assertTrue(result instanceof ListenableWorker.Result.Failure);
    }

    @Test
    public void signatureProviderWithoutErrorStreamReturnsDeterministicFailure() throws Exception {
        ensureNullHttpHandlerRegistered();
        AndroidAssemblyUploadWorker worker = TestListenableWorkerBuilder
                .from(ApplicationProvider.getApplicationContext(), AndroidAssemblyUploadWorker.class)
                .build();

        Method method = AndroidAssemblyUploadWorker.class.getDeclaredMethod(
                "buildSignatureProvider", String.class, String.class, java.util.Map.class);
        method.setAccessible(true);

        Object providerObj = method.invoke(worker, "nullhttp://signature.test", "POST", Collections.emptyMap());
        com.transloadit.sdk.SignatureProvider provider = (com.transloadit.sdk.SignatureProvider) providerObj;

        try {
            provider.generateSignature("{}");
            fail("Expected RequestException");
        } catch (RequestException ex) {
            assertTrue(ex.getMessage().contains("500"));
        }
    }

    @Test
    public void workerDoesNotLeakExecutorThreads() throws Exception {
        File missing = new File(temporaryFolder.getRoot(), "does_not_exist.bin");
        AndroidAssemblyWorkConfig config = AndroidAssemblyWorkConfig.newBuilder("key", "secret")
                .paramsJson("{\"steps\":{\"noop\":{\"robot\":\"/video/encode\"}}}")
                .addFile(missing, "file")
                .build();

        Set<String> before = currentPoolThreadNames();

        AndroidAssemblyUploadWorker worker = TestListenableWorkerBuilder
                .from(ApplicationProvider.getApplicationContext(), AndroidAssemblyUploadWorker.class)
                .setInputData(config.toInputData())
                .build();

        ListenableWorker.Result result = worker.startWork().get();
        assertTrue(result instanceof ListenableWorker.Result.Failure);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            Set<String> leaked = currentPoolThreadNames();
            leaked.removeAll(before);
            if (leaked.isEmpty()) {
                return;
            }
            Thread.sleep(50);
        }
        Set<String> leaked = currentPoolThreadNames();
        leaked.removeAll(before);
        fail("Detected leaked executor threads: " + leaked);
    }

    @Test
    public void statusUpdateFailureUnblocksLatch() throws Exception {
        File temp = temporaryFolder.newFile("input.txt");
        AndroidAssemblyWorkConfig config = AndroidAssemblyWorkConfig.newBuilder("key", "secret")
                .addFile(temp, "file")
                .paramsJson("{\"steps\":{\"noop\":{\"robot\":\"/video/encode\"}}}")
                .completionTimeoutMillis(200)
                .build();

        StatusFailureWorker worker = TestListenableWorkerBuilder
                .from(ApplicationProvider.getApplicationContext(), StatusFailureWorker.class)
                .setInputData(config.toInputData())
                .build();

        long start = System.nanoTime();
        ListenableWorker.Result result = worker.startWork().get();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(result instanceof ListenableWorker.Result.Failure);
        assertTrue("Expected latch to unblock quickly, took " + elapsedMs + "ms", elapsedMs < 500);
    }

    private static Set<String> currentPoolThreadNames() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> !thread.isDaemon())
                .map(Thread::getName)
                .filter(name -> name.startsWith("pool-"))
                .collect(Collectors.toSet());
    }

    private static volatile boolean handlerRegistered = false;

    private static void ensureNullHttpHandlerRegistered() {
        if (handlerRegistered) {
            return;
        }
        synchronized (AndroidAssemblyUploadWorkerTest.class) {
            if (handlerRegistered) {
                return;
            }
            try {
                URL.setURLStreamHandlerFactory(new NullHttpHandlerFactory());
            } catch (Error ignored) {
                // Factory already registered elsewhere; nothing to do.
            }
            handlerRegistered = true;
        }
    }

    private static class NullHttpHandlerFactory implements URLStreamHandlerFactory {
        @Override
        public URLStreamHandler createURLStreamHandler(String protocol) {
            if ("nullhttp".equals(protocol)) {
                return new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL u) {
                        return new NullHttpURLConnection(u);
                    }
                };
            }
            return null;
        }
    }

    private static class NullHttpURLConnection extends HttpURLConnection {
        protected NullHttpURLConnection(URL url) {
            super(url);
        }

        @Override
        public void disconnect() { }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() { }

        @Override
        public int getResponseCode() {
            return 500;
        }

        @Override
        public InputStream getInputStream() {
            return null;
        }

        @Override
        public InputStream getErrorStream() {
            return null;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }
    }

    public static class StatusFailureWorker extends AndroidAssemblyUploadWorker {
        public StatusFailureWorker(Context context, androidx.work.WorkerParameters params) {
            super(context, params);
        }

        @Override
        protected AndroidAssembly createAssembly(AndroidTransloadit transloadit, AndroidAssemblyListener listener) {
            return new StatusFailureAssembly(transloadit, listener, getApplicationContext());
        }
    }

    private static class StatusFailureAssembly extends AndroidAssembly {
        private final AndroidAssemblyListener listener;

        StatusFailureAssembly(AndroidTransloadit transloadit, AndroidAssemblyListener listener, Context context) {
            super(transloadit, listener, context);
            this.listener = listener;
        }

        @Override
        public Future<AssemblyResponse> saveAsync(boolean isResumable) {
            Request request = new Request.Builder().url("https://example.com/assembly").build();
            Response response = new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ResponseBody.create("{}", MediaType.get("application/json")))
                    .build();
            AssemblyResponse assemblyResponse;
            try {
                assemblyResponse = new AssemblyResponse(response);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            listener.onAssemblyStatusUpdateFailed(new RequestException("status failure"));
            return CompletableFuture.completedFuture(assemblyResponse);
        }
    }
}
