package com.transloadit.examples.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.transloadit.android.sdk.AndroidAssemblyWorkConfig
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkManagerSampleTest {
    private lateinit var context: Context

    companion object {
        @Volatile
        private var workManagerInitialized = false
    }

    @Before
    fun setUp() {
        val e2eEnabled = System.getenv("ANDROID_SDK_E2E")?.toBoolean() == true
        assumeTrue("Skipping WorkManager E2E test", e2eEnabled)
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        if (!workManagerInitialized) {
            synchronized(WorkManagerSampleTest::class) {
                if (!workManagerInitialized) {
                    try {
                        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
                    } catch (_: IllegalStateException) {
                        // Already initialized for this process.
                    }
                    workManagerInitialized = true
                }
            }
        }
    }

    @Test
    fun workRequestWithSecretCompletes() {
        val authKeyValue = System.getenv("TRANSLOADIT_KEY")
        val authSecretValue = System.getenv("TRANSLOADIT_SECRET")
        assumeTrue(!authKeyValue.isNullOrBlank())
        assumeTrue(!authSecretValue.isNullOrBlank())
        val authKey = authKeyValue!!
        val authSecret = authSecretValue!!

        val sampleFile = createTempFile()
        val paramsJson = """{"steps":{"resize":{"robot":"/image/resize","width":32,"height":32,"result":true}}}"""

        val request = WorkManagerSample.enqueueUpload(context, authKey, authSecret, paramsJson, sampleFile)
        waitForSuccess(request.id)
    }

    @Test
    fun signatureProviderConfigCanBeBuilt() {
        val config = AndroidAssemblyWorkConfig.newBuilder("key")
            .signatureProvider("https://example.com/sign")
            .signatureProviderMethod("POST")
            .addSignatureProviderHeader("Authorization", "Bearer token")
            .paramsJson("{\"steps\":{\"noop\":{\"robot\":\"/file/import\"}}}")
            .addFile(createTempFile(), "file")
            .build()

        assertEquals("https://example.com/sign", config.getSignatureProviderUrl())
        assertEquals("POST", config.getSignatureProviderMethod())
        assertEquals("Bearer token", config.getSignatureProviderHeaders()["Authorization"])
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

    private fun waitForSuccess(id: UUID) {
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!
        testDriver.setAllConstraintsMet(id)

        val workManager = WorkManager.getInstance(context)
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
        var info: WorkInfo
        do {
            info = workManager.getWorkInfoById(id).get(10, TimeUnit.SECONDS)
            if (info.state == WorkInfo.State.SUCCEEDED) {
                break
            }
            Thread.sleep(1_000)
        } while (info.state == WorkInfo.State.RUNNING && System.currentTimeMillis() < deadline)

        assertTrue("Worker should succeed", info.state == WorkInfo.State.SUCCEEDED)
    }

}
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
