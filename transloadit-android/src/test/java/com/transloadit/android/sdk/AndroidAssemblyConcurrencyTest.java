package com.transloadit.android.sdk;

import static org.junit.Assert.assertSame;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AndroidAssemblyConcurrencyTest {

    private static final AndroidAssemblyListener NOOP_LISTENER = new AndroidAssemblyListener() {};

    @Test
    public void assembliesShareExecutorInstance() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        AndroidTransloadit transloadit = new AndroidTransloadit("key", "secret");

        AndroidAssembly first = new AndroidAssembly(transloadit, NOOP_LISTENER, context);
        AndroidAssembly second = new AndroidAssembly(transloadit, NOOP_LISTENER, context);

        ExecutorService firstExecutor = executorOf(first);
        ExecutorService secondExecutor = executorOf(second);

        assertSame(firstExecutor, secondExecutor);
    }

    private static ExecutorService executorOf(AndroidAssembly assembly) throws Exception {
        Field field = AndroidAssembly.class.getDeclaredField("executor");
        field.setAccessible(true);
        return (ExecutorService) field.get(assembly);
    }
}
