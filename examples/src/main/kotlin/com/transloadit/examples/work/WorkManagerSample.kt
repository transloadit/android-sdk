package com.transloadit.examples.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.transloadit.android.sdk.AndroidAssemblyUploadWorker
import com.transloadit.android.sdk.AndroidAssemblyWorkConfig
import java.io.File

/**
 * Helper for enqueuing Transloadit uploads via WorkManager.
 */
object WorkManagerSample {
    private const val UNIQUE_WORK_NAME = "transloadit-workmanager-sample"

    fun enqueueUpload(
        context: Context,
        authKey: String,
        authSecret: String,
        paramsJson: String,
        file: File,
        field: String = "file"
    ): OneTimeWorkRequest {
        val config = AndroidAssemblyWorkConfig
            .newBuilder(authKey, authSecret)
            .paramsJson(paramsJson)
            .addFile(file, field)
            .waitForCompletion(true)
            .build()

        val request = config.toWorkRequest()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        return request
    }

    fun enqueueUploadWithSignatureProvider(
        context: Context,
        authKey: String,
        signatureEndpoint: String,
        paramsJson: String,
        file: File,
        field: String = "file",
        headers: Map<String, String> = emptyMap(),
        method: String = "POST"
    ): OneTimeWorkRequest {
        val builder = AndroidAssemblyWorkConfig
            .newBuilder(authKey)
            .signatureProvider(signatureEndpoint)
            .signatureProviderMethod(method)
            .paramsJson(paramsJson)
            .addFile(file, field)
            .waitForCompletion(true)

        headers.forEach { (key, value) -> builder.addSignatureProviderHeader(key, value) }

        val request = builder.build().toWorkRequest()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        return request
    }
}
