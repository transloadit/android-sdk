package com.transloadit.examples.work

import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class WorkManagerSampleTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        val e2eEnabled = System.getenv("ANDROID_SDK_E2E")?.toBoolean() == true
        assumeTrue("Skipping WorkManager E2E test", e2eEnabled)
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun workRequestCompletes() {
        val authKeyValue = System.getenv("TRANSLOADIT_KEY")
        val authSecretValue = System.getenv("TRANSLOADIT_SECRET")
        assumeTrue(!authKeyValue.isNullOrBlank())
        assumeTrue(!authSecretValue.isNullOrBlank())
        val authKey = authKeyValue!!
        val authSecret = authSecretValue!!

        val sampleFile = createTempFile()
        val paramsJson = """{"steps":{"resize":{"robot":"/image/resize","width":32,"height":32,"result":true}}}"""

        val request = WorkManagerSample.enqueueUpload(context, authKey, authSecret, paramsJson, sampleFile)

        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!
        testDriver.setAllConstraintsMet(request.id)

        val workManager = WorkManager.getInstance(context)
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
        var info: WorkInfo
        do {
            info = workManager.getWorkInfoById(request.id).get(10, TimeUnit.SECONDS)
            if (info.state == WorkInfo.State.SUCCEEDED) {
                break
            }
            Thread.sleep(1_000)
        } while (info.state == WorkInfo.State.RUNNING && System.currentTimeMillis() < deadline)

        assertTrue("Worker should succeed", info.state == WorkInfo.State.SUCCEEDED)
    }

    private fun createTempFile(): File {
        val file = File.createTempFile("transloadit-e2e-sample", ".bin")
        file.outputStream().use { out ->
            val bytes = ByteArray(4 * 1024)
            Random.nextBytes(bytes)
            repeat(16) { out.write(bytes) }
        }
        return file
    }
}
