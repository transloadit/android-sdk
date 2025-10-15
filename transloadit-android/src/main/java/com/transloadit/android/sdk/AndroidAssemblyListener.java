package com.transloadit.android.sdk;

import com.transloadit.sdk.response.AssemblyResponse;

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
}
