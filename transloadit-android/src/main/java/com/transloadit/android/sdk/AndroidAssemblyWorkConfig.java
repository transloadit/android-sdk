package com.transloadit.android.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Immutable configuration object used to enqueue background Transloadit uploads via WorkManager.
 */
public final class AndroidAssemblyWorkConfig {
    static final String CONFIG_JSON_KEY = "transloadit.work.config";
    private static final String CONFIG_VERSION = "1";

    private final String authKey;
    private final String authSecret;
    private final String signatureProviderUrl;
    private final String signatureProviderMethod;
    private final Map<String, String> signatureProviderHeaders;
    private final String hostUrl;
    private final boolean resumable;
    private final boolean waitForCompletion;
    private final long completionTimeoutMillis;
    private final long uploadTimeoutMillis;
    private final String preferenceName;
    private final JSONObject params;
    private final List<FileSpec> files;

    private AndroidAssemblyWorkConfig(Builder builder) {
        this.authKey = builder.authKey;
        this.authSecret = builder.authSecret;
        this.hostUrl = builder.hostUrl;
        this.resumable = builder.resumable;
        this.waitForCompletion = builder.waitForCompletion;
        this.completionTimeoutMillis = builder.completionTimeoutMillis;
        this.uploadTimeoutMillis = builder.uploadTimeoutMillis;
        this.preferenceName = builder.preferenceName;
        this.params = builder.params == null ? new JSONObject() : builder.params;
        this.files = Collections.unmodifiableList(new ArrayList<>(builder.files));
        this.signatureProviderUrl = builder.signatureProviderUrl;
        this.signatureProviderMethod = builder.signatureProviderMethod;
        this.signatureProviderHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(builder.signatureProviderHeaders));
    }

    /**
     * @return Transloadit API key used for the assembly.
     */
    public String getAuthKey() {
        return authKey;
    }

    /**
     * @return Transloadit API secret, or {@code null} when using an external signature provider.
     */
    public String getAuthSecret() {
        return authSecret;
    }

    /**
     * @return URL of the external signature provider, if configured.
     */
    @Nullable
    public String getSignatureProviderUrl() {
        return signatureProviderUrl;
    }

    /**
     * @return HTTP method used when calling the signature provider.
     */
    @Nullable
    public String getSignatureProviderMethod() {
        return signatureProviderMethod;
    }

    /**
     * @return additional headers attached to signature provider requests.
     */
    public Map<String, String> getSignatureProviderHeaders() {
        return signatureProviderHeaders;
    }

    /**
     * @return Transloadit API host URL.
     */
    public String getHostUrl() {
        return hostUrl;
    }

    /**
     * @return {@code true} when resumable uploads are enabled.
     */
    public boolean isResumable() {
        return resumable;
    }

    /**
     * @return {@code true} when the worker should wait for assembly completion.
     */
    public boolean shouldWaitForCompletion() {
        return waitForCompletion;
    }

    /**
     * @return maximum time in milliseconds to wait for completion status updates.
     */
    public long getCompletionTimeoutMillis() {
        return completionTimeoutMillis;
    }

    /**
     * @return maximum time in milliseconds to wait for the initial save response.
     */
    public long getUploadTimeoutMillis() {
        return uploadTimeoutMillis;
    }

    /**
     * @return SharedPreferences file name used for resumable upload metadata.
     */
    public String getPreferenceName() {
        return preferenceName;
    }

    /**
     * @return assembly params payload (steps, fields, options).
     */
    public JSONObject getParams() {
        return params;
    }

    /**
     * @return immutable list of files to upload.
     */
    public List<FileSpec> getFiles() {
        return files;
    }

    /**
     * Serializes this configuration into WorkManager input data.
     *
     * @return {@link Data} representation of the config
     */
    public Data toInputData() {
        Data.Builder builder = new Data.Builder();
        builder.putString(CONFIG_JSON_KEY, toJson().toString());
        return builder.build();
    }

    /**
     * Serializes the configuration to JSON for persistence.
     *
     * @return JSON representation of the config
     */
    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("version", CONFIG_VERSION);
            json.put("authKey", authKey);
            if (authSecret != null) {
                json.put("authSecret", authSecret);
            }
            json.put("hostUrl", hostUrl);
            json.put("resumable", resumable);
            json.put("waitForCompletion", waitForCompletion);
            json.put("completionTimeoutMillis", completionTimeoutMillis);
            json.put("uploadTimeoutMillis", uploadTimeoutMillis);
            json.put("preferenceName", preferenceName == null ? JSONObject.NULL : preferenceName);
            json.put("params", params);
            JSONArray fileArray = new JSONArray();
            for (FileSpec spec : files) {
                fileArray.put(spec.toJson());
            }
            json.put("files", fileArray);
            if (signatureProviderUrl != null) {
                JSONObject sig = new JSONObject();
                sig.put("url", signatureProviderUrl);
                sig.put("method", signatureProviderMethod);
                JSONObject headers = new JSONObject();
                for (Map.Entry<String, String> entry : signatureProviderHeaders.entrySet()) {
                    headers.put(entry.getKey(), entry.getValue());
                }
                sig.put("headers", headers);
                json.put("signatureProvider", sig);
            }
            return json;
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to serialize work config", e);
        }
    }

    /**
     * Creates a WorkManager request that uploads the configured assembly.
     *
     * @return configured {@link OneTimeWorkRequest}
     */
    public OneTimeWorkRequest toWorkRequest() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        return new OneTimeWorkRequest.Builder(AndroidAssemblyUploadWorker.class)
                .setInputData(toInputData())
                .setConstraints(constraints)
                .build();
    }

    /**
     * Restores a configuration from its JSON form.
     *
     * @param json serialized configuration
     * @return parsed {@link AndroidAssemblyWorkConfig}
     */
    public static AndroidAssemblyWorkConfig fromJson(JSONObject json) {
        try {
            String version = json.optString("version", "");
            if (!CONFIG_VERSION.equals(version)) {
                throw new IllegalArgumentException("Unsupported config version: " + version);
            }
            Builder builder = new Builder(json.getString("authKey"));
            if (json.has("authSecret")) {
                builder.authSecret(json.getString("authSecret"));
            }
            String host = json.optString("hostUrl", null);
            if (host != null && !host.isEmpty()) {
                builder.hostUrl(host);
            }
            builder.resumable(json.optBoolean("resumable", true));
            builder.waitForCompletion(json.optBoolean("waitForCompletion", true));
            builder.completionTimeoutMillis(json.optLong("completionTimeoutMillis", Builder.DEFAULT_COMPLETION_TIMEOUT_MS));
            builder.uploadTimeoutMillis(json.optLong("uploadTimeoutMillis", Builder.DEFAULT_UPLOAD_TIMEOUT_MS));
            String pref = json.optString("preferenceName", null);
            if (pref != null && !pref.isEmpty() && !JSONObject.NULL.equals(pref)) {
                builder.preferenceName(pref);
            }
            builder.params(json.optJSONObject("params"));
            JSONArray filesArray = json.optJSONArray("files");
            if (filesArray != null) {
                for (int i = 0; i < filesArray.length(); i++) {
                    JSONObject item = filesArray.getJSONObject(i);
                    builder.addFile(new File(item.getString("path")), item.getString("field"));
                }
            }
            JSONObject sig = json.optJSONObject("signatureProvider");
            if (sig != null) {
                builder.signatureProvider(sig.getString("url"));
                builder.signatureProviderMethod(sig.optString("method", "POST"));
                JSONObject headers = sig.optJSONObject("headers");
                if (headers != null) {
                    Iterator<String> headerKeys = headers.keys();
                    while (headerKeys.hasNext()) {
                        String key = headerKeys.next();
                        builder.addSignatureProviderHeader(key, headers.optString(key, ""));
                    }
                }
            }
            return builder.build();
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid Transloadit work configuration", e);
        }
    }

    /**
     * Restores a configuration from WorkManager input data.
     *
     * @param data input data provided to the worker
     * @return parsed {@link AndroidAssemblyWorkConfig}
     */
    public static AndroidAssemblyWorkConfig fromInputData(Data data) {
        String json = data.getString(CONFIG_JSON_KEY);
        if (json == null) {
            throw new IllegalArgumentException("Missing Transloadit work configuration");
        }
        try {
            return fromJson(new JSONObject(json));
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid Transloadit work configuration", e);
        }
    }

    /**
     * Creates a new builder configured with inline credentials.
     *
     * @param authKey Transloadit API key
     * @param authSecret Transloadit API secret
     * @return builder instance
     */
    public static Builder newBuilder(@NonNull String authKey, @NonNull String authSecret) {
        Builder builder = new Builder(authKey);
        builder.authSecret(authSecret);
        return builder;
    }

    /**
     * Creates a new builder that expects an external signature provider.
     *
     * @param authKey Transloadit API key
     * @return builder instance
     */
    public static Builder newBuilder(@NonNull String authKey) {
        return new Builder(authKey);
    }

    /**
     * Builder for {@link AndroidAssemblyWorkConfig}.
     */
    public static final class Builder {
        static final long DEFAULT_COMPLETION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15);
        static final long DEFAULT_UPLOAD_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15);

        private final String authKey;
        private String authSecret;
        private String hostUrl = AndroidTransloadit.DEFAULT_HOST_URL;
        private boolean resumable = true;
        private boolean waitForCompletion = true;
        private long completionTimeoutMillis = DEFAULT_COMPLETION_TIMEOUT_MS;
        private long uploadTimeoutMillis = DEFAULT_UPLOAD_TIMEOUT_MS;
        private String preferenceName = AndroidAssembly.DEFAULT_PREFERENCE_NAME;
        private JSONObject params;
        private final List<FileSpec> files = new ArrayList<>();
        private String signatureProviderUrl;
        private String signatureProviderMethod = "POST";
        private final Map<String, String> signatureProviderHeaders = new LinkedHashMap<>();

        private Builder(String authKey) {
            this.authKey = Objects.requireNonNull(authKey, "authKey");
        }

        /**
         * Sets the Transloadit API secret for inline signing.
         *
         * @param authSecret secret corresponding to {@code authKey}
         * @return this builder
         */
        public Builder authSecret(@Nullable String authSecret) {
            this.authSecret = authSecret;
            return this;
        }

        /**
         * Overrides the Transloadit API host URL.
         *
         * @param hostUrl custom host URL, or {@code null} to use the default
         * @return this builder
         */
        public Builder hostUrl(@Nullable String hostUrl) {
            if (hostUrl != null && !hostUrl.isEmpty()) {
                this.hostUrl = hostUrl;
            }
            return this;
        }

        /**
         * Enables or disables resumable uploads.
         *
         * @param resumable {@code true} to enable tus resumable uploads
         * @return this builder
         */
        public Builder resumable(boolean resumable) {
            this.resumable = resumable;
            return this;
        }

        /**
         * Configures whether the worker should wait for assembly completion.
         *
         * @param waitForCompletion {@code true} to wait for completion before finishing the job
         * @return this builder
         */
        public Builder waitForCompletion(boolean waitForCompletion) {
            this.waitForCompletion = waitForCompletion;
            return this;
        }

        /**
         * Sets the timeout for waiting on completion status updates.
         *
         * @param completionTimeoutMillis timeout in milliseconds
         * @return this builder
         */
        public Builder completionTimeoutMillis(long completionTimeoutMillis) {
            if (completionTimeoutMillis <= 0) {
                throw new IllegalArgumentException("completionTimeoutMillis must be positive");
            }
            this.completionTimeoutMillis = completionTimeoutMillis;
            return this;
        }

        /**
         * Sets the timeout for waiting on the initial upload response.
         *
         * @param uploadTimeoutMillis timeout in milliseconds
         * @return this builder
         */
        public Builder uploadTimeoutMillis(long uploadTimeoutMillis) {
            if (uploadTimeoutMillis <= 0) {
                throw new IllegalArgumentException("uploadTimeoutMillis must be positive");
            }
            this.uploadTimeoutMillis = uploadTimeoutMillis;
            return this;
        }

        /**
         * Overrides the SharedPreferences file name used for tus metadata.
         *
         * @param preferenceName name of the preference file
         * @return this builder
         */
        public Builder preferenceName(@NonNull String preferenceName) {
            this.preferenceName = Objects.requireNonNull(preferenceName, "preferenceName");
            return this;
        }

        /**
         * Copies assembly params into the configuration.
         *
         * @param params JSON payload containing steps, fields and options
         * @return this builder
         */
        public Builder params(@Nullable JSONObject params) {
            if (params != null) {
                try {
                    this.params = new JSONObject(params.toString());
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Invalid params", e);
                }
            } else {
                this.params = null;
            }
            return this;
        }

        /**
         * Parses assembly params from a JSON string.
         *
         * @param paramsJson JSON string representation of assembly params
         * @return this builder
         */
        public Builder paramsJson(@NonNull String paramsJson) {
            try {
                this.params = new JSONObject(paramsJson);
            } catch (JSONException e) {
                throw new IllegalArgumentException("Invalid params JSON", e);
            }
            return this;
        }

        /**
         * Adds a local file to be uploaded with the assembly.
         *
         * @param file file on disk
         * @param field field name for the file
         * @return this builder
         */
        public Builder addFile(@NonNull File file, @NonNull String field) {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(field, "field");
            files.add(new FileSpec(file.getAbsolutePath(), field));
            return this;
        }

        /**
         * Configures an external signature provider endpoint. When specified,
         * {@link AndroidAssemblyUploadWorker} will fetch signatures over HTTP instead of using a locally supplied secret.
         *
         * @param url URL of the signature provider endpoint
         * @return this builder
         */
        public Builder signatureProvider(@NonNull String url) {
            this.signatureProviderUrl = Objects.requireNonNull(url, "url");
            return this;
        }

        /**
         * Overrides the HTTP method used for the signature provider request. Defaults to {@code POST}.
         *
         * @param method HTTP method to use
         * @return this builder
         */
        public Builder signatureProviderMethod(@NonNull String method) {
            this.signatureProviderMethod = Objects.requireNonNull(method, "method").toUpperCase();
            return this;
        }

        /**
         * Adds a header that will be included when contacting the external signature provider.
         *
         * @param key header name
         * @param value header value
         * @return this builder
         */
        public Builder addSignatureProviderHeader(@NonNull String key, @NonNull String value) {
            this.signatureProviderHeaders.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Validates the provided data and builds the immutable configuration.
         *
         * @return finalized {@link AndroidAssemblyWorkConfig}
         */
        public AndroidAssemblyWorkConfig build() {
            if ((authSecret == null || authSecret.isEmpty()) && signatureProviderUrl == null) {
                throw new IllegalStateException("Either authSecret or signatureProvider must be provided");
            }
            if (files.isEmpty()) {
                throw new IllegalStateException("At least one file must be added");
            }
            return new AndroidAssemblyWorkConfig(this);
        }
    }

    /**
     * Immutable description of a file that should be uploaded.
     */
    public static final class FileSpec {
        private final String path;
        private final String field;

        FileSpec(String path, String field) {
            this.path = path;
            this.field = field;
        }

        /**
         * @return absolute file path on disk.
         */
        public String getPath() {
            return path;
        }

        /**
         * @return form field name associated with the file.
         */
        public String getField() {
            return field;
        }

        JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("path", path);
                json.put("field", field);
                return json;
            } catch (JSONException e) {
                throw new IllegalStateException("Failed to serialize file spec", e);
            }
        }
    }

    static JSONObject toJSONObject(Data data) {
        String json = data.getString(CONFIG_JSON_KEY);
        if (json == null) {
            throw new IllegalArgumentException("Missing config json");
        }
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid config json", e);
        }
    }

    static void applyParamsToAssembly(AndroidAssembly assembly, JSONObject params) throws JSONException {
        if (params == null) {
            return;
        }
        JSONObject steps = params.optJSONObject("steps");
        if (steps != null) {
            Iterator<String> stepKeys = steps.keys();
            while (stepKeys.hasNext()) {
                String stepName = stepKeys.next();
                JSONObject stepObject = steps.getJSONObject(stepName);
                MapBuilder mapBuilder = new MapBuilder(stepObject);
                String robot = mapBuilder.removeString("robot");
                if (robot != null && !robot.isEmpty()) {
                    assembly.addStep(stepName, robot, mapBuilder.build());
                } else {
                    assembly.addStep(stepName, mapBuilder.build());
                }
            }
        }

        JSONObject fields = params.optJSONObject("fields");
        if (fields != null) {
            assembly.addFields(new MapBuilder(fields).build());
        }

        Iterator<String> keys = params.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if ("steps".equals(key) || "fields".equals(key) || "auth".equals(key)) {
                continue;
            }
            Object value = convertJsonValue(params.get(key));
            assembly.addOption(key, value);
        }
    }

    private static Object convertJsonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject) {
            return new MapBuilder((JSONObject) value).build();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> list = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                list.add(convertJsonValue(array.opt(i)));
            }
            return list;
        }
        if (value == JSONObject.NULL) {
            return null;
        }
        return value;
    }

    private static final class MapBuilder {
        private final JSONObject source;

        MapBuilder(JSONObject source) {
            this.source = source;
        }

        @Nullable
        String removeString(String key) {
            if (!source.has(key)) {
                return null;
            }
            Object value = source.remove(key);
            if (value == null || value == JSONObject.NULL) {
                return null;
            }
            return value.toString();
        }

        java.util.Map<String, Object> build() {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            Iterator<String> keys = source.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = source.opt(key);
                map.put(key, convertJsonValue(value));
            }
            return map;
        }
    }
}
