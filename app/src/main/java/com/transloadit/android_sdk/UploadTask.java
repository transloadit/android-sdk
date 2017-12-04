package com.transloadit.android_sdk;

import android.os.AsyncTask;

import java.io.IOException;

import io.tus.java.client.ProtocolException;
import io.tus.java.client.TusClient;
import io.tus.java.client.TusUpload;
import io.tus.java.client.TusUploader;


public class UploadTask extends AsyncTask<Void, Long, Void> {
    private Exception exception;
    private TusClient client;
    private TusUpload upload;
    private ActivityAssembly assembly;

    public UploadTask(ActivityAssembly assembly, TusClient client, TusUpload upload) {
        this.assembly = assembly;
        this.client = client;
        this.upload = upload;
    }

    @Override
    protected void onProgressUpdate(Long... updates) {
        long uploadedBytes = updates[0];
        long totalBytes = updates[1];

        assembly.listener.getProgressBar().setProgress((int) ((double) uploadedBytes / totalBytes * 100));
    }

    @Override
    protected void onPostExecute(Void v) {
        assembly.onUploadFinished();
    }

    @Override
    protected void onCancelled() {
        if (exception != null) {
            assembly.onUploadFailed(exception);
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        try {
            TusUploader uploader = client.resumeOrCreateUpload(upload);
            long uploadedBytes;
            long totalBytes = upload.getSize();

            uploader.setChunkSize(5 * 1024 *1024); // 5MB
            uploader.setRequestPayloadSize(5 * 1024 * 1024); // 5MB

            while (!isCancelled() && uploader.uploadChunk() > 0) {
                uploadedBytes = uploader.getOffset();
                publishProgress(uploadedBytes, totalBytes);
            }
            uploader.finish();
        } catch (ProtocolException | IOException e) {
            exception = e;
            cancel(true);
        }

        return null;
    }
}
