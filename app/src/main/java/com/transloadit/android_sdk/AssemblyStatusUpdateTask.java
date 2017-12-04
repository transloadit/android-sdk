package com.transloadit.android_sdk;

import android.os.AsyncTask;

import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

/**
 * Created by ifedapo on 02/12/2017.
 */

public class AssemblyStatusUpdateTask extends AsyncTask<Void, Void, AssemblyResponse> {
    private ActivityAssembly assembly;
    private Exception exception;

    public AssemblyStatusUpdateTask(ActivityAssembly assembly) {
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
                response = assembly.getClient().getAssemblyByUrl(assembly.url);
            } while (!response.isFinished());
            return response;
        } catch (RequestException | LocalOperationException e) {
            cancel(true);
        }

        return null;
    }
}
