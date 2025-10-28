package com.transloadit.android.sdk;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.transloadit.sdk.SignatureProvider;
import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
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

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** Output key exposing the created assembly id. */
    public static final String OUTPUT_ASSEMBLY_ID = "assembly_id";
    /** Output key exposing the HTTP URL of the assembly. */
    public static final String OUTPUT_ASSEMBLY_URL = "assembly_url";
    /** Output key exposing the HTTPS URL of the assembly. */
    public static final String OUTPUT_SSL_URL = "assembly_ssl_url";

    /**
     * Creates a new worker instance.
     *
     * @param appContext application context used for dependency resolution
     * @param workerParams WorkManager parameters for this execution
     */
    public AndroidAssemblyUploadWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    /**
     * Executes the assembly upload synchronously on a worker thread.
     *
     * @return WorkManager {@link Result} describing the outcome
     */
    @NonNull
    @Override
    public Result doWork() {
        AndroidAssemblyWorkConfig config;
        try {
            config = AndroidAssemblyWorkConfig.fromInputData(getInputData());
        } catch (IllegalArgumentException e) {
            return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
        }

        AndroidTransloadit transloadit;
        String authSecret = config.getAuthSecret();
        String signatureUrl = config.getSignatureProviderUrl();
        if (authSecret != null && !authSecret.isEmpty()) {
            if (config.getHostUrl() == null) {
                transloadit = new AndroidTransloadit(config.getAuthKey(), authSecret);
            } else {
                transloadit = new AndroidTransloadit(config.getAuthKey(), authSecret, config.getHostUrl());
            }
        } else if (signatureUrl != null) {
            SignatureProvider provider = buildSignatureProvider(signatureUrl,
                    config.getSignatureProviderMethod(),
                    config.getSignatureProviderHeaders());
            if (config.getHostUrl() == null) {
                transloadit = new AndroidTransloadit(config.getAuthKey(), provider);
            } else {
                transloadit = new AndroidTransloadit(config.getAuthKey(), provider, config.getHostUrl());
            }
        } else {
            return Result.failure(new Data.Builder().putString("error", "Missing authSecret or signature provider").build());
        }

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
        try {
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
                return handleFailure(completionError.get());
            } catch (Exception e) {
                completionError.compareAndSet(null, e);
                return handleFailure(e);
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
                return handleFailure(completionException);
            }

            AssemblyResponse finalResponse = completionResponse.get() != null ? completionResponse.get() : initial;
            JSONObject json = finalResponse.json();
            Data output = new Data.Builder()
                    .putString(OUTPUT_ASSEMBLY_ID, finalResponse.getId())
                    .putString(OUTPUT_ASSEMBLY_URL, json.optString("assembly_url"))
                    .putString(OUTPUT_SSL_URL, finalResponse.getSslUrl())
                    .build();
            return Result.success(output);
        } finally {
            try {
                assembly.close();
            } catch (IOException ignored) {
                // Best effort to terminate executor; ignore close failures.
            }
        }
    }

    /**
     * Maps assembly exceptions to WorkManager outcomes.
     *
     * @param error failure emitted by the assembly
     * @return {@link Result#failure(Data)} for deterministic errors, {@link Result#retry()} otherwise
     */
    private Result handleFailure(Exception error) {
        if (error instanceof RequestException || error instanceof LocalOperationException) {
            Data output = new Data.Builder()
                    .putString("error", error.getMessage())
                    .build();
            return Result.failure(output);
        }
        return Result.retry();
    }

    /**
     * Builds a signature provider that proxies calls to a remote HTTP endpoint.
     *
     * @param url URL of the signature provider
     * @param method HTTP method to use (defaults to POST)
     * @param headers headers to include in requests
     * @return {@link SignatureProvider} that fetches signatures over HTTP
     */
    private SignatureProvider buildSignatureProvider(String url, String method, java.util.Map<String, String> headers) {
        final String httpMethod = method == null ? "POST" : method.toUpperCase();
        final java.util.Map<String, String> requestHeaders = headers == null ? java.util.Collections.emptyMap() : headers;
        return paramsJson -> {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(httpMethod);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(15_000);
            connection.setDoInput(true);
            for (java.util.Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            if ("POST".equals(httpMethod) || "PUT".equals(httpMethod)) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = new BufferedOutputStream(connection.getOutputStream())) {
                    os.write(paramsJson.getBytes(UTF8));
                }
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = "";
            try {
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        response = sb.toString();
                    }
                } else if (code < 200 || code >= 300) {
                    throw new RequestException("Signature provider returned status " + code + " with empty body");
                } else {
                    throw new RequestException("Signature provider response missing body");
                }
            } finally {
                connection.disconnect();
            }

            if (code < 200 || code >= 300) {
                throw new RequestException("Signature provider returned status " + code + ": " + response);
            }

            JSONObject json = new JSONObject(response);
            String signature = json.optString("signature", null);
            if (signature == null || signature.isEmpty()) {
                throw new RequestException("Signature provider response missing signature field");
            }
            return signature;
        };
    }
}
