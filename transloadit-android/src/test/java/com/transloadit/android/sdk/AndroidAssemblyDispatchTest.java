package com.transloadit.android.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.transloadit.sdk.AssemblyListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AndroidAssemblyDispatchTest {

    private AndroidTransloadit transloadit;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        transloadit = new AndroidTransloadit("key", "secret");
    }

    @Test
    public void callbacksDefaultToMainThread() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> ranOnMain = new AtomicReference<>(false);

        AndroidAssemblyListener listener = new AndroidAssemblyListener() {
            @Override
            public void onUploadFinished() {
                ranOnMain.set(Looper.myLooper() == Looper.getMainLooper());
                latch.countDown();
            }
        };

        AndroidAssembly assembly = new AndroidAssembly(transloadit, listener, context);
        AssemblyListener adapter = assembly.createListenerAdapterForTesting();

        ExecutorService background = Executors.newSingleThreadExecutor();
        Future<?> dispatched = background.submit(() -> {
            adapter.onAssemblyUploadFinished();
            return null;
        });

        dispatched.get(5, TimeUnit.SECONDS);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertTrue("Callback not invoked", latch.await(5, TimeUnit.SECONDS));
        assertTrue("Callback should run on main thread", Boolean.TRUE.equals(ranOnMain.get()));

        background.shutdownNow();
        assembly.close();
    }

    @Test
    public void callbacksCanOptOutOfMainThread() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Thread> callbackThread = new AtomicReference<>();

        AndroidAssemblyListener listener = new AndroidAssemblyListener() {
            @Override
            public void onUploadFinished() {
                callbackThread.set(Thread.currentThread());
                latch.countDown();
            }
        };

        AndroidAssembly assembly = new AndroidAssembly(transloadit, listener, context);
        assembly.useDirectCallbacks();
        AssemblyListener adapter = assembly.createListenerAdapterForTesting();

        ExecutorService background = Executors.newSingleThreadExecutor();
        Future<Thread> taskThread = background.submit(() -> {
            Thread dispatchThread = Thread.currentThread();
            adapter.onAssemblyUploadFinished();
            return dispatchThread;
        });

        assertTrue("Callback not invoked", latch.await(5, TimeUnit.SECONDS));
        Thread dispatchThread = taskThread.get(5, TimeUnit.SECONDS);
        assertSame("Callback should execute on dispatch thread", dispatchThread, callbackThread.get());

        background.shutdownNow();
        assembly.close();
    }

    @Test
    public void pauseAndResumeHelpersSucceedWithoutUploads() throws Exception {
        AndroidAssemblyListener listener = new AndroidAssemblyListener() { };
        AndroidAssembly assembly = new AndroidAssembly(transloadit, listener, context);
        try {
            assertTrue(assembly.pauseUploadsSafely());
            assertTrue(assembly.resumeUploadsSafely());
        } finally {
            assembly.close();
        }
    }
}
