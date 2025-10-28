package com.transloadit.android.sdk;

import com.transloadit.sdk.response.AssemblyResponse;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Listener for receiving lifecycle callbacks during Android assembly execution.
 */
public interface AndroidAssemblyListener {

    /**
     * Called periodically with upload progress.
     *
     * @param uploadedBytes number of bytes uploaded so far
     * @param totalBytes total bytes expected for the upload
     */
    default void onUploadProgress(long uploadedBytes, long totalBytes) {
    }

    /**
     * Called when all uploads have finished successfully.
     */
    default void onUploadFinished() {
    }

    /**
     * Called when the upload fails before completion.
     *
     * @param exception error that caused the failure
     */
    default void onUploadFailed(Exception exception) {
    }

    /**
     * Called once the assembly has finished processing on Transloadit.
     *
     * @param response final assembly response
     */
    default void onAssemblyFinished(AssemblyResponse response) {
    }

    /**
     * Called if polling or SSE updates encounter an error.
     *
     * @param exception error emitted by the status update mechanism
     */
    default void onAssemblyStatusUpdateFailed(Exception exception) {
    }

    /**
     * Called with periodic assembly progress updates.
     *
     * @param progressPerOriginalFile progress details keyed by original file
     */
    default void onAssemblyProgress(JSONObject progressPerOriginalFile) {
    }

    /**
     * Called when individual assembly results become available.
     *
     * @param result array containing result entries
     */
    default void onAssemblyResultFinished(JSONArray result) {
    }
}
