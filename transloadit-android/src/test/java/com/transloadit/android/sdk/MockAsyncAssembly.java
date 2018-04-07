package com.transloadit.android.sdk;

import android.app.Activity;

import com.transloadit.sdk.Transloadit;
import com.transloadit.sdk.async.AssemblyProgressListener;
import com.transloadit.sdk.response.AssemblyResponse;

import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;

import io.tus.java.client.ProtocolException;
import io.tus.java.client.TusClient;
import io.tus.java.client.TusUpload;
import io.tus.java.client.TusUploader;

public class MockAsyncAssembly extends AndroidAsyncAssembly {
    public MockAsyncAssembly(AndroidTransloadit transloadit, AssemblyProgressListener listener, Activity activity) {
        super(transloadit, listener, activity);
        tusClient = new MockTusClient();
    }

    static class MockTusClient extends TusClient {
        @Override
        public TusUploader resumeOrCreateUpload(@NotNull TusUpload upload) throws ProtocolException, IOException {
            TusUploader uploader = Mockito.mock(TusUploader.class);
            // 1077 / 3 = 359 i.e size of the LICENSE file
            Mockito.when(uploader.uploadChunk()).thenReturn(359,359, 359, 0, -1);
            return uploader;
        }
    }

    @Override
    protected void startExecutor() {
        AssemblyStatusUpdateTask statusUpdateTask = Mockito.mock(AssemblyStatusUpdateTask.class);
        Mockito.when(statusUpdateTask.execute()).thenAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                getListener().onAssemblyFinished(Mockito.mock(AssemblyResponse.class));
                return null;
            }
        });
        executor = new MockExecutor(statusUpdateTask);
        executor.execute();
    }

    class MockExecutor extends AndroidAsyncAssembly.AsyncAssemblyExecutorImpl {
        MockExecutor(AssemblyStatusUpdateTask statusUpdateTask) {
            super(statusUpdateTask);
        }

        @Override
        public void execute() {
            onPostExecute(doInBackground());
        }
    }
}
