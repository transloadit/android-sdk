package com.transloadit.android.sdk;

import android.os.AsyncTask;

import com.transloadit.sdk.AsyncAssemblyExecutor;

class AsyncAssemblyExecutorImpl extends AsyncTask<Void, Long, Void> implements AsyncAssemblyExecutor {
    private AndroidAsyncAssembly asyncAssembly;
    private AssemblyStatusUpdateTask statusUpdateTask;
    private Runnable runnable;
    private boolean stopped;
    private Exception exception;

    public AsyncAssemblyExecutorImpl(AndroidAsyncAssembly asyncAssembly,
                                     Runnable runnable, AssemblyStatusUpdateTask statusUpdateTask) {
        this.asyncAssembly = asyncAssembly;
        this.runnable = runnable;
        this.statusUpdateTask = statusUpdateTask;
        stopped = false;
    }

    @Override
    protected void onProgressUpdate(Long... updates) {
        long uploadedBytes = updates[0];
        long totalBytes = updates[1];

        asyncAssembly.getListener().onUploadPogress(uploadedBytes, totalBytes);
    }

    @Override
    protected void onPostExecute(Void v) {
        asyncAssembly.getListener().onUploadFinished();
        statusUpdateTask.execute();
    }

    @Override
    protected void onCancelled() {
        stopped = true;
        if (exception != null) {
            asyncAssembly.getListener().onUploadFailed(exception);
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        runnable.run();
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
        cancel(false);
        while (!stopped) {
            // do nothing but wait until it is stopped.
        }
    }

    void setError(Exception exception) {
        this.exception = exception;
    }
}
