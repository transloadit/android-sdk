package com.transloadit.android.sdk;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.junit.runner.RunWith;

import java.io.File;

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
}
