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
    }

    public String getAuthKey() {
        return authKey;
    }

    public String getAuthSecret() {
        return authSecret;
    }

    public String getHostUrl() {
        return hostUrl;
    }

    public boolean isResumable() {
        return resumable;
    }

    public boolean shouldWaitForCompletion() {
        return waitForCompletion;
    }

    public long getCompletionTimeoutMillis() {
        return completionTimeoutMillis;
    }

    public long getUploadTimeoutMillis() {
        return uploadTimeoutMillis;
    }

    public String getPreferenceName() {
        return preferenceName;
    }

    public JSONObject getParams() {
        return params;
    }

    public List<FileSpec> getFiles() {
        return files;
    }

    public Data toInputData() {
        Data.Builder builder = new Data.Builder();
        builder.putString(CONFIG_JSON_KEY, toJson().toString());
        return builder.build();
    }

    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("version", CONFIG_VERSION);
            json.put("authKey", authKey);
            json.put("authSecret", authSecret);
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
            return json;
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to serialize work config", e);
        }
    }

    public OneTimeWorkRequest toWorkRequest() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        return new OneTimeWorkRequest.Builder(AndroidAssemblyUploadWorker.class)
                .setInputData(toInputData())
                .setConstraints(constraints)
                .build();
    }

    public static AndroidAssemblyWorkConfig fromJson(JSONObject json) {
        try {
            String version = json.optString("version", "");
            if (!CONFIG_VERSION.equals(version)) {
                throw new IllegalArgumentException("Unsupported config version: " + version);
            }
            Builder builder = new Builder(json.getString("authKey"), json.getString("authSecret"));
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
            return builder.build();
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid Transloadit work configuration", e);
        }
    }

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

    public static Builder newBuilder(@NonNull String authKey, @NonNull String authSecret) {
        return new Builder(authKey, authSecret);
    }

    public static final class Builder {
        static final long DEFAULT_COMPLETION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15);
        static final long DEFAULT_UPLOAD_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15);

        private final String authKey;
        private final String authSecret;
        private String hostUrl = AndroidTransloadit.DEFAULT_HOST_URL;
        private boolean resumable = true;
        private boolean waitForCompletion = true;
        private long completionTimeoutMillis = DEFAULT_COMPLETION_TIMEOUT_MS;
        private long uploadTimeoutMillis = DEFAULT_UPLOAD_TIMEOUT_MS;
        private String preferenceName = AndroidAssembly.DEFAULT_PREFERENCE_NAME;
        private JSONObject params;
        private final List<FileSpec> files = new ArrayList<>();

        private Builder(String authKey, String authSecret) {
            this.authKey = Objects.requireNonNull(authKey, "authKey");
            this.authSecret = Objects.requireNonNull(authSecret, "authSecret");
        }

        public Builder hostUrl(@Nullable String hostUrl) {
            if (hostUrl != null && !hostUrl.isEmpty()) {
                this.hostUrl = hostUrl;
            }
            return this;
        }

        public Builder resumable(boolean resumable) {
            this.resumable = resumable;
            return this;
        }

        public Builder waitForCompletion(boolean waitForCompletion) {
            this.waitForCompletion = waitForCompletion;
            return this;
        }

        public Builder completionTimeoutMillis(long completionTimeoutMillis) {
            if (completionTimeoutMillis <= 0) {
                throw new IllegalArgumentException("completionTimeoutMillis must be positive");
            }
            this.completionTimeoutMillis = completionTimeoutMillis;
            return this;
        }

        public Builder uploadTimeoutMillis(long uploadTimeoutMillis) {
            if (uploadTimeoutMillis <= 0) {
                throw new IllegalArgumentException("uploadTimeoutMillis must be positive");
            }
            this.uploadTimeoutMillis = uploadTimeoutMillis;
            return this;
        }

        public Builder preferenceName(@NonNull String preferenceName) {
            this.preferenceName = Objects.requireNonNull(preferenceName, "preferenceName");
            return this;
        }

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

        public Builder paramsJson(@NonNull String paramsJson) {
            try {
                this.params = new JSONObject(paramsJson);
            } catch (JSONException e) {
                throw new IllegalArgumentException("Invalid params JSON", e);
            }
            return this;
        }

        public Builder addFile(@NonNull File file, @NonNull String field) {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(field, "field");
            files.add(new FileSpec(file.getAbsolutePath(), field));
            return this;
        }

        public AndroidAssemblyWorkConfig build() {
            if (files.isEmpty()) {
                throw new IllegalStateException("At least one file must be added");
            }
            return new AndroidAssemblyWorkConfig(this);
        }
    }

    public static final class FileSpec {
        private final String path;
        private final String field;

        FileSpec(String path, String field) {
            this.path = path;
            this.field = field;
        }

        public String getPath() {
            return path;
        }

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
