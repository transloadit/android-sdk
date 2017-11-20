package com.transloadit.android_sdk;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;

import com.transloadit.sdk.Assembly;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import io.tus.android.client.TusAndroidUpload;
import io.tus.android.client.TusPreferencesURLStore;
import io.tus.java.client.ProtocolException;
import io.tus.java.client.TusClient;
import io.tus.java.client.TusExecutor;
import io.tus.java.client.TusUpload;
import io.tus.java.client.TusUploader;


public class ActivityAssembly extends Assembly {
    Activity activity;
    Map<String, Uri> files;

    public ActivityAssembly(com.transloadit.sdk.Transloadit transloadit, Activity activity) {
        super(transloadit);
        this.activity = activity;
    }

    public void addFile(String name, Uri fileUri) {
        files.put(name, fileUri);
    }

    protected void processTusFiles(String assemblyUrl) throws IOException, ProtocolException {
        tusClient = new TusClient();

        SharedPreferences pref = activity.getSharedPreferences("tus", 0);
        tusClient.setUploadCreationURL(new URL(transloadit.getHostUrl() + "/resumable/files/"));
        tusClient.enableResuming(new TusPreferencesURLStore(pref));

        for (Map.Entry<String, Uri> entry : files.entrySet()) {
            processTusFile(entry.getValue(), entry.getKey(), assemblyUrl);
        }
    }

    protected void processTusFile(Uri file, String fieldName, String assemblyUrl)
            throws IOException, ProtocolException {

        final TusUpload upload = new TusAndroidUpload(file, activity);

        Map<String, String> metadata = upload.getMetadata();
        metadata.put("assembly_url", assemblyUrl);
        metadata.put("fieldname", fieldName);

        upload.setMetadata(metadata);

        TusExecutor executor = new TusExecutor() {
            @Override
            protected void makeAttempt() throws ProtocolException, IOException {
                TusUploader uploader = tusClient.resumeOrCreateUpload(upload);
                uploader.setChunkSize(2 * 1024 * 1024); // 2MB

                int uploadedChunk = 0;
                while (uploadedChunk > -1) {
                    uploadedChunk = uploader.uploadChunk();
                }
                uploader.finish();
            }
        };

        executor.makeAttempts();
    }
}
