package com.transloadit.android.sdk;

import android.content.Context;
import android.content.SharedPreferences;

import com.transloadit.sdk.Assembly;
import com.transloadit.sdk.AssemblyListener;
import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.tus.android.client.TusPreferencesURLStore;
import io.tus.java.client.TusURLStore;

/**
 * Android-friendly Assembly wrapper that runs uploads asynchronously and reports progress via
 * {@link AndroidAssemblyListener} on a background thread.
 */
public class AndroidAssembly extends Assembly implements Closeable {
    public static final String DEFAULT_PREFERENCE_NAME = "transloadit_android_sdk_urls";

    private final Context context;
    private final AndroidAssemblyListener listener;
    private final ExecutorService executor;

    public AndroidAssembly(AndroidTransloadit transloadit, AndroidAssemblyListener listener, Context context) {
        super(transloadit);
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.executor = Executors.newSingleThreadExecutor();
        setPreferenceName(DEFAULT_PREFERENCE_NAME);
    }

    /**
     * Applies a shared preferences backed Tus URL store for resumable uploads.
     */
    public void setPreferenceName(String name) {
        SharedPreferences pref = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        TusURLStore store = new TusPreferencesURLStore(pref);
        setTusURLStore(store);
    }

    /**
     * Runs the assembly asynchronously, returning the initial save response.
     */
    public Future<AssemblyResponse> saveAsync() {
        return saveAsync(true);
    }

    public Future<AssemblyResponse> saveAsync(boolean isResumable) {
        setAssemblyListener(createListenerAdapter());
        Callable<AssemblyResponse> task = () -> {
            try {
                return AndroidAssembly.super.save(isResumable);
            } catch (LocalOperationException | RequestException e) {
                listener.onUploadFailed(e);
                throw e;
            }
        };
        return executor.submit(task);
    }

    private AssemblyListener createListenerAdapter() {
        return new AssemblyListener() {
            @Override
            public void onAssemblyFinished(AssemblyResponse response) {
                listener.onAssemblyFinished(response);
            }

            @Override
            public void onError(Exception error) {
                listener.onAssemblyStatusUpdateFailed(error);
            }

            @Override
            public void onMetadataExtracted() {
                // no-op
            }

            @Override
            public void onAssemblyUploadFinished() {
                listener.onUploadFinished();
            }

            @Override
            public void onFileUploadFinished(JSONObject uploadInformation) {
                // no-op
            }

            @Override
            public void onFileUploadPaused(String name) {
                // no-op
            }

            @Override
            public void onFileUploadResumed(String name) {
                // no-op
            }

            @Override
            public void onFileUploadProgress(long uploadedBytes, long totalBytes) {
                listener.onUploadProgress(uploadedBytes, totalBytes);
            }

            @Override
            public void onAssemblyProgress(org.json.JSONObject progressPerOriginalFile) {
                // no-op
            }

            @Override
            public void onAssemblyResultFinished(JSONArray result) {
                // no-op
            }
        };
    }

    @Override
    public void close() throws IOException {
        executor.shutdownNow();
    }
}
