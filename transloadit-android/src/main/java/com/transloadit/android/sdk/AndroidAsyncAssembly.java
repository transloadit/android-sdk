package com.transloadit.android.sdk;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import com.transloadit.sdk.Assembly;
import com.transloadit.sdk.async.AssemblyProgressListener;
import com.transloadit.sdk.async.AsyncAssembly;
import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import java.io.IOException;
import java.util.concurrent.Callable;

import io.tus.android.client.TusPreferencesURLStore;
import io.tus.java.client.ProtocolException;


/**
 * This class represents a new assembly being created.
 * It is similar to {@link Assembly} but provides Asynchronous functionality.
 */
public class AndroidAsyncAssembly extends AsyncAssembly {
    private String preferenceName;
    private Activity activity;

    public static final String DEFAULT_PREFERENCE_NAME = "tansloadit_android_sdk_urls";


    /**
     * A new instance of {@link AndroidAsyncAssembly}
     *
     * @param transloadit {@link AndroidTransloadit} the transloadit client
     * @param listener an implementation of {@link AssemblyProgressListener}
     * @param activity {@link Activity} the activity where this assembly creation is taking place
     */
    public AndroidAsyncAssembly(AndroidTransloadit transloadit, AssemblyProgressListener listener, Activity activity) {
        super(transloadit, listener);
        this.activity = activity;
        setPreferenceName(DEFAULT_PREFERENCE_NAME);
    }

    /**
     * Set the Activity storage preference name
     *
     * @param name set the storage preference name
     */
    public void setPreferenceName(String name) {
        preferenceName = name;
        SharedPreferences pref = activity.getSharedPreferences(preferenceName, 0);
        setTusURLStore(new TusPreferencesURLStore(pref));
    }

    @Override
    protected void startExecutor() {
        AssemblyStatusUpdateCallable statusUpdateRunnable = new AssemblyStatusUpdateCallable();
        AssemblyStatusUpdateTask statusUpdateTask = new AssemblyStatusUpdateTask(this, statusUpdateRunnable);
        statusUpdateRunnable.setExecutor(statusUpdateTask);

        executor = new AsyncAssemblyExecutorImpl(statusUpdateTask);
        executor.execute();
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

    class AsyncAssemblyExecutorImpl extends AsyncTask<Void, Long, Void> implements AsyncAssemblyExecutor {
        private AssemblyStatusUpdateTask statusUpdateTask;
        private Exception exception;

        public AsyncAssemblyExecutorImpl(AssemblyStatusUpdateTask statusUpdateTask) {
            this.statusUpdateTask = statusUpdateTask;
        }

        @Override
        protected void onPostExecute(Void v) {
            getListener().onUploadFinished();
            statusUpdateTask.execute();
        }

        @Override
        protected void onCancelled() {
            if (exception != null) {
                getListener().onUploadFailed(exception);
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                uploadTusFiles();
            } catch (IOException | ProtocolException e) {
                setError(e);
                stop();
            }
            return null;
        }

        @Override
        public void execute() {
            super.execute();
        }

        @Override
        public void stop() {
            cancel(false);
        }

        @Override
        public void hardStop() {
            cancel(true);
        }

        void setError(Exception exception) {
            this.exception = exception;
        }
    }
}
