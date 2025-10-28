package com.transloadit.android.sdk;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.transloadit.sdk.exceptions.RequestException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
}
