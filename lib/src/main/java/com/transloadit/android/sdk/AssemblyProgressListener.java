package com.transloadit.android.sdk;

import android.app.Activity;
import android.widget.ProgressBar;

import com.transloadit.sdk.response.AssemblyResponse;


/**
 * Implementations of this interface are used to handle progress and completeion of a background
 * Assembly file upload and execution
 */
public interface AssemblyProgressListener {
    /**
     *
     * Retreive the ProgressBar to which the Assembly upload progress should be transmitted.
     *
     * @return ProgressBar to update upload progress
     */
    ProgressBar getProgressBar();

    /**
     * Callback to be executed when the Assembly upload is complete
     */
    void onUploadFinished();

    /**
     * Callback to be executed when the Assembly execution is done executing.
     * This encompasses any kind of termination of the assembly.
     * Including when the assembly aborts due to failure.
     *
     * @param {@link AssemblyResponse} response with the updated status of the assembly.
     */
    void onAssemblyFinished(AssemblyResponse response);

    /**
     * Callback to be executed if the Assembly upload fails.
     * @param exception the error that causes the failure.
     */
    void onUploadFailed(Exception exception);

    /**
     * Callback to be executed if the Assembly status update retrieve fails
     * @param exception the error that causes the failure.
     */
    void onAssemblyStatusUpdateFailed(Exception exception);

    /**
     *
     * @return The related Activity from which this execution stems.
     */
    Activity getActivity();
}
