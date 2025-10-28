package com.transloadit.android.sdk;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WorkManager worker that executes a Transloadit assembly in the background.
 */
public class AndroidAssemblyUploadWorker extends Worker {

    public static final String OUTPUT_ASSEMBLY_ID = "assembly_id";
    public static final String OUTPUT_ASSEMBLY_URL = "assembly_url";
    public static final String OUTPUT_SSL_URL = "assembly_ssl_url";

    public AndroidAssemblyUploadWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        AndroidAssemblyWorkConfig config;
        try {
            config = AndroidAssemblyWorkConfig.fromInputData(getInputData());
        } catch (IllegalArgumentException e) {
            return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
        }

        AndroidTransloadit transloadit = config.getHostUrl() == null
                ? new AndroidTransloadit(config.getAuthKey(), config.getAuthSecret())
                : new AndroidTransloadit(config.getAuthKey(), config.getAuthSecret(), config.getHostUrl());

        CountDownLatch completionLatch = config.shouldWaitForCompletion() ? new CountDownLatch(1) : null;
        AtomicReference<AssemblyResponse> completionResponse = new AtomicReference<>();
        AtomicReference<Exception> completionError = new AtomicReference<>();

        AndroidAssemblyListener listener = new AndroidAssemblyListener() {
            @Override
            public void onUploadFailed(Exception exception) {
                completionError.compareAndSet(null, exception);
                if (completionLatch != null) {
                    completionLatch.countDown();
                }
            }

            @Override
            public void onAssemblyStatusUpdateFailed(Exception exception) {
                completionError.compareAndSet(null, exception);
            }

            @Override
            public void onAssemblyFinished(AssemblyResponse response) {
                completionResponse.set(response);
                if (completionLatch != null) {
                    completionLatch.countDown();
                }
            }
        };

        AndroidAssembly assembly = transloadit.newAssembly(listener, getApplicationContext());
        assembly.useDirectCallbacks();
        if (config.getPreferenceName() != null) {
            assembly.setPreferenceName(config.getPreferenceName());
        }

        List<AndroidAssemblyWorkConfig.FileSpec> files = config.getFiles();
        for (AndroidAssemblyWorkConfig.FileSpec spec : files) {
            File file = new File(spec.getPath());
            if (!file.exists()) {
                return Result.failure(new Data.Builder()
                        .putString("error", "File not found: " + spec.getPath())
                        .build());
            }
            assembly.addFile(file, spec.getField());
        }

        try {
            AndroidAssemblyWorkConfig.applyParamsToAssembly(assembly, config.getParams());
        } catch (Exception e) {
            return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
        }

        Future<AssemblyResponse> future = assembly.saveAsync(config.isResumable());
        AssemblyResponse initial;
        try {
            initial = future.get(config.getUploadTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof Exception) {
                completionError.compareAndSet(null, (Exception) cause);
            }
            return Result.retry();
        } catch (Exception e) {
            completionError.compareAndSet(null, e);
            return Result.retry();
        }

        if (config.shouldWaitForCompletion()) {
            try {
                boolean finished = completionLatch.await(config.getCompletionTimeoutMillis(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    return Result.retry();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.retry();
            }
        }

        Exception completionException = completionError.get();
        if (completionException != null) {
            boolean retryable = completionException instanceof RequestException
                    || completionException instanceof LocalOperationException;
            Data output = new Data.Builder()
                    .putString("error", completionException.getMessage())
                    .build();
            return retryable ? Result.retry() : Result.failure(output);
        }

        AssemblyResponse finalResponse = completionResponse.get() != null ? completionResponse.get() : initial;
        JSONObject json = finalResponse.json();
        Data output = new Data.Builder()
                .putString(OUTPUT_ASSEMBLY_ID, finalResponse.getId())
                .putString(OUTPUT_ASSEMBLY_URL, json.optString("assembly_url"))
                .putString(OUTPUT_SSL_URL, finalResponse.getSslUrl())
                .build();
        return Result.success(output);
    }
}
