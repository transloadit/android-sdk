package com.transloadit.android.sdk;

import android.os.AsyncTask;

import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

/**
 * Created by ifedapo on 02/12/2017.
 */

public class AssemblyStatusUpdateTask extends AsyncTask<Void, Void, AssemblyResponse> {
    private Assembly assembly;
    private Exception exception;

    public AssemblyStatusUpdateTask(Assembly assembly) {
        this.assembly = assembly;
    }

    @Override
    protected void onPostExecute(AssemblyResponse response) {
        assembly.onFinished(response);
    }

    @Override
    protected void onCancelled() {
        if (exception != null) {
            assembly.onStatusUpdateFailed(exception);
        }
    }

    @Override
    protected AssemblyResponse doInBackground(Void... params) {
        try {
            AssemblyResponse response;
            do {
                response = assembly.getClient().getAssemblyByUrl(assembly.getUrl());
                Thread.sleep(1000);
            } while (!response.isFinished());
            return response;
        } catch (RequestException | LocalOperationException | InterruptedException e) {
            cancel(true);
        }

        return null;
    }
}
