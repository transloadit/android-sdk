package com.transloadit.android.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.transloadit.sdk.Assembly;
import com.transloadit.sdk.AssemblyListener;
import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.tus.android.client.TusPreferencesURLStore;
import io.tus.java.client.TusURLStore;

/**
 * Android-friendly Assembly wrapper that runs uploads asynchronously and reports progress via
 * {@link AndroidAssemblyListener}. Callbacks are dispatched on the main thread by default and can be
 * rerouted to a custom executor via {@link #setListenerCallbackExecutor(Executor)}.
 */
public class AndroidAssembly extends Assembly implements Closeable {
    public static final String DEFAULT_PREFERENCE_NAME = "transloadit_android_sdk_urls"; // NOTE: renamed from "tansloadit_" in 0.x; existing persisted uploads will not resume automatically.

    private final Context context;
    private final AndroidAssemblyListener listener;
    private final ExecutorService executor;
    private final Executor mainThreadExecutor;
    private volatile Executor listenerExecutor;

    public AndroidAssembly(AndroidTransloadit transloadit, AndroidAssemblyListener listener, Context context) {
        super(transloadit);
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainThreadExecutor = new MainThreadExecutor();
        this.listenerExecutor = this.mainThreadExecutor;
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
                dispatchListener(l -> l.onUploadFailed(e));
                throw e;
            } catch (Exception e) {
                dispatchListener(l -> l.onUploadFailed(e));
                throw e;
            }
        };
        return executor.submit(task);
    }

    public void setListenerCallbackExecutor(Executor executor) {
        this.listenerExecutor = Objects.requireNonNull(executor, "listener executor cannot be null");
    }

    public void useMainThreadCallbacks() {
        this.listenerExecutor = this.mainThreadExecutor;
    }

    public void useDirectCallbacks() {
        this.listenerExecutor = Runnable::run;
    }

    public boolean pauseUploadsSafely() {
        try {
            super.pauseUploads();
            return true;
        } catch (LocalOperationException e) {
            dispatchListener(l -> l.onAssemblyStatusUpdateFailed(e));
            return false;
        }
    }

    public boolean resumeUploadsSafely() {
        try {
            super.resumeUploads();
            return true;
        } catch (LocalOperationException | RequestException e) {
            dispatchListener(l -> l.onAssemblyStatusUpdateFailed(e));
            return false;
        }
    }

    @androidx.annotation.VisibleForTesting
    Executor getListenerExecutorForTesting() {
        return listenerExecutor;
    }

    @androidx.annotation.VisibleForTesting
    AssemblyListener createListenerAdapterForTesting() {
        return createListenerAdapter();
    }

    private AssemblyListener createListenerAdapter() {
        return new AssemblyListener() {
            @Override
            public void onAssemblyFinished(AssemblyResponse response) {
                dispatchListener(l -> l.onAssemblyFinished(response));
            }

            @Override
            public void onError(Exception error) {
                dispatchListener(l -> l.onAssemblyStatusUpdateFailed(error));
            }

            @Override
            public void onMetadataExtracted() {
                // no-op
            }

            @Override
            public void onAssemblyUploadFinished() {
                dispatchListener(AndroidAssemblyListener::onUploadFinished);
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
                dispatchListener(l -> l.onUploadProgress(uploadedBytes, totalBytes));
            }

            @Override
            public void onAssemblyProgress(org.json.JSONObject progressPerOriginalFile) {
                dispatchListener(l -> l.onAssemblyProgress(progressPerOriginalFile));
            }

            @Override
            public void onAssemblyResultFinished(JSONArray result) {
                dispatchListener(l -> l.onAssemblyResultFinished(result));
            }
        };
    }

    private void dispatchListener(ListenerAction action) {
        Executor executor = listenerExecutor;
        executor.execute(() -> action.invoke(listener));
    }

    @Override
    public void close() throws IOException {
        executor.shutdownNow();
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            if (Looper.myLooper() == handler.getLooper()) {
                command.run();
            } else {
                handler.post(command);
            }
        }
    }

    @FunctionalInterface
    interface ListenerAction {
        void invoke(AndroidAssemblyListener listener);
    }
}
