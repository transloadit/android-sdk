package com.transloadit.android.sdk;

import com.transloadit.sdk.response.AssemblyResponse;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Listener for receiving lifecycle callbacks during Android assembly execution.
 */
public interface AndroidAssemblyListener {

    default void onUploadProgress(long uploadedBytes, long totalBytes) {
    }

    default void onUploadFinished() {
    }

    default void onUploadFailed(Exception exception) {
    }

    default void onAssemblyFinished(AssemblyResponse response) {
    }

    default void onAssemblyStatusUpdateFailed(Exception exception) {
    }

    default void onAssemblyProgress(JSONObject progressPerOriginalFile) {
    }

    default void onAssemblyResultFinished(JSONArray result) {
    }
}
