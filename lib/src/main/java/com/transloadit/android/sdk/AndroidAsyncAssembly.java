package com.transloadit.android.sdk;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;

import com.transloadit.sdk.async.AssemblyProgressListener;
import com.transloadit.sdk.async.AsyncAssembly;
import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Callable;

import io.tus.android.client.TusAndroidUpload;
import io.tus.android.client.TusPreferencesURLStore;
import io.tus.java.client.ProtocolException;
import io.tus.java.client.TusUpload;


public class AndroidAsyncAssembly extends AsyncAssembly {
    private String preferenceName;
    private Activity activity;

    public static final String DEFAULT_PREFERENCE_NAME = "tansloadit_android_sdk_urls";


    public AndroidAsyncAssembly(Transloadit transloadit, AssemblyProgressListener listener, Activity activity) {
        super(transloadit, listener);
        preferenceName = DEFAULT_PREFERENCE_NAME;
        SharedPreferences pref = activity.getSharedPreferences(preferenceName, 0);
        setTusURLStore(new TusPreferencesURLStore(pref));
        this.activity = activity;
    }

    /**
     *
     * @param name set the storage preference name
     */
    public void setPreferenceName(String name) {
        preferenceName = name;
    }

    @Override
    protected TusUpload getTusUploadInstance(File file) throws FileNotFoundException {
        return new TusAndroidUpload(Uri.fromFile(file), activity);
    }

    @Override
    protected void startExecutor() {
        AssemblyStatusUpdateCallable statusUpdateRunnable = new AssemblyStatusUpdateCallable();
        AssemblyStatusUpdateTask statusUpdateTask = new AssemblyStatusUpdateTask(this, statusUpdateRunnable);
        statusUpdateRunnable.setExecutor(statusUpdateTask);

        AssemblyRunnable assemblyRunnable = new AssemblyRunnable();
        executor = new AsyncAssemblyExecutorImpl(this,
                assemblyRunnable, statusUpdateTask);

        assemblyRunnable.setExecutor((AsyncAssemblyExecutorImpl)executor);

        executor.execute();
    }

    class AssemblyRunnable implements Runnable {
        private AsyncAssemblyExecutorImpl executor;

        void setExecutor(AsyncAssemblyExecutorImpl executor) {
            this.executor = executor;
        }

        @Override
        public void run() {
            try {
                uploadTusFiles();
            } catch (IOException | ProtocolException e) {
                executor.setError(e);
                executor.stop();
            }
        }
    }

    class AssemblyStatusUpdateCallable implements Callable<AssemblyResponse> {
        private AssemblyStatusUpdateTask executor;

        void setExecutor(AssemblyStatusUpdateTask executor) {
            this.executor = executor;
        }

        @Override
        public AssemblyResponse call() {
            try {
                return watchStatus();
            } catch (RequestException | LocalOperationException e) {
                executor.setError(e);
                executor.cancel(false);
            }

            return null;
        }
    }
}
