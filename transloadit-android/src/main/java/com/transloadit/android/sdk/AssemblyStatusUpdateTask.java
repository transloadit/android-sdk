package com.transloadit.android.sdk;

import android.os.AsyncTask;

import com.transloadit.sdk.response.AssemblyResponse;

import java.util.concurrent.Callable;

/**
 * This class helps us run a watch on an assembly status in an async manner.
 */
class AssemblyStatusUpdateTask extends AsyncTask<Void, Void, AssemblyResponse> {
    private AndroidAsyncAssembly assembly;
    private Exception exception;
    private Callable<AssemblyResponse> callable;

    public AssemblyStatusUpdateTask(AndroidAsyncAssembly assembly, Callable<AssemblyResponse> callable) {
        this.assembly = assembly;
        this.callable = callable;
    }

    @Override
    protected void onPostExecute(AssemblyResponse response) {
        assembly.getListener().onAssemblyFinished(response);
    }

    @Override
    protected void onCancelled() {
        if (exception != null) {
            assembly.getListener().onAssemblyStatusUpdateFailed(exception);
        }
    }

    @Override
    protected AssemblyResponse doInBackground(Void... params) {
        try {
            return callable.call();
        } catch (Exception e) {
            setError(e);
            cancel(false);
        }

        return null;
    }

    void setError(Exception exception) {
        this.exception = exception;
    }
}