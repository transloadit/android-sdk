package com.transloadit.android.sdk;

import android.content.SharedPreferences;
import android.net.Uri;

import com.transloadit.sdk.response.AssemblyResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.tus.android.client.TusAndroidUpload;
import io.tus.android.client.TusPreferencesURLStore;
import io.tus.java.client.ProtocolException;
import io.tus.java.client.TusUpload;


public class Assembly extends com.transloadit.sdk.Assembly {
    protected Transloadit transloadit;
    protected  Map<String, Uri> fileUris;

    private AssemblyProgressListener listener;
    private List<UploadTask> uploadTasks;
    private AssemblyStatusUpdateTask statusUpdateTask;
    private String preferenceName;
    private String url;

    public static final String DEFAULT_PREFERENCE_NAME = "tansloadit_android_sdk_urls";


    public Assembly(Transloadit transloadit, AssemblyProgressListener listener) {
        super(transloadit);
        this.transloadit = transloadit;
        this.listener = listener;
        fileUris = new HashMap<String, Uri>();
        uploadTasks = new ArrayList<UploadTask>();
        preferenceName = DEFAULT_PREFERENCE_NAME;
    }

    @Override
    public Transloadit getClient() {
        return this.transloadit;
    }

    @Override
    public int getFilesCount() {
        return fileUris.size();
    }

    public void setPreferenceName(String name) {
        preferenceName = name;
    }

    AssemblyProgressListener getListener() {
        return listener;
    }

    String getUrl() {
        return url;
    }

    public void addFile(String name, Uri fileUri) {
        fileUris.put(name, fileUri);
    }

    public void pauseUpload() {
        for (UploadTask task : uploadTasks) {
            task.cancel(false);
        }
    }

    public void resumeUpload() throws IOException, ProtocolException {
        processTusFiles(url);
    }

    void onUploadFinished() {
        listener.onUploadFinished();
        startStatusUpdateTask();
    }

    void onFinished(AssemblyResponse response) {
        listener.onAssemblyFinished(response);
    }

    void onUploadFailed(Exception exception) {
        listener.onUploadFailed(exception);
    }

    void onStatusUpdateFailed(Exception exception) {
        listener.onAssemblyStatusUpdateFailed(exception);
    }

    @Override
    protected void processTusFiles(String assemblyUrl) throws IOException, ProtocolException {
        setUpTusClient();
        url = assemblyUrl;
        uploadTasks = new ArrayList<UploadTask>();

        for (Map.Entry<String, Uri> entry : fileUris.entrySet()) {
            processTusFile(entry.getValue(), entry.getKey(), assemblyUrl);
        }
    }

    @Override
    protected void setUpTusClient() throws IOException {
        super.setUpTusClient();
        SharedPreferences pref = listener.getActivity().getSharedPreferences(preferenceName, 0);
        tusClient.enableResuming(new TusPreferencesURLStore(pref));
    }

    protected void processTusFile(Uri fileUri, String fieldName, String assemblyUrl)
            throws IOException, ProtocolException {

        final TusUpload upload = new TusAndroidUpload(fileUri, listener.getActivity());

        Map<String, String> metadata = upload.getMetadata();
        metadata.put("assembly_url", assemblyUrl);
        metadata.put("fieldname", fieldName);

        upload.setMetadata(metadata);
        UploadTask uploadTask = new UploadTask(this, tusClient, upload);
        uploadTasks.add(uploadTask);
        uploadTask.execute();
    }

    private void startStatusUpdateTask() {
        statusUpdateTask = new AssemblyStatusUpdateTask(this);
        statusUpdateTask.execute();
    }
}
