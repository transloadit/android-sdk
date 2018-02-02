package com.transloadit.android.sdk;

import android.os.AsyncTask;

import com.transloadit.sdk.async.AsyncAssemblyExecutor;

/**
 * Handles the async execution of an assembly.
 */
class AsyncAssemblyExecutorImpl extends AsyncTask<Void, Long, Void> implements AsyncAssemblyExecutor {
    private AndroidAsyncAssembly asyncAssembly;
    private AssemblyStatusUpdateTask statusUpdateTask;
    private Runnable runnable;
    private Exception exception;

    public AsyncAssemblyExecutorImpl(AndroidAsyncAssembly asyncAssembly,
                                     Runnable runnable, AssemblyStatusUpdateTask statusUpdateTask) {
        this.asyncAssembly = asyncAssembly;
        this.runnable = runnable;
        this.statusUpdateTask = statusUpdateTask;
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
        cancel(true);
    }

    void setError(Exception exception) {
        this.exception = exception;
    }
}
