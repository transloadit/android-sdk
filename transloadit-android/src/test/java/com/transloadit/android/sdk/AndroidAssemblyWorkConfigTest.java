package com.transloadit.android.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class AndroidAssemblyWorkConfigTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void roundTripSerializationWorks() throws Exception {
        File file = temporaryFolder.newFile("upload.bin");
        AndroidAssemblyWorkConfig config = AndroidAssemblyWorkConfig.newBuilder("key", "secret")
                .hostUrl("https://api2.transloadit.com")
                .paramsJson("{\"steps\":{\"resize\":{\"robot\":\"/image/resize\",\"width\":32}}}")
                .addFile(file, "image")
                .preferenceName("custom_store")
                .completionTimeoutMillis(90_000)
                .uploadTimeoutMillis(120_000)
                .waitForCompletion(true)
                .resumable(true)
                .build();

        JSONObject json = config.toJson();
        AndroidAssemblyWorkConfig restored = AndroidAssemblyWorkConfig.fromJson(json);

        assertEquals("key", restored.getAuthKey());
        assertEquals("secret", restored.getAuthSecret());
        assertEquals("https://api2.transloadit.com", restored.getHostUrl());
        assertEquals("custom_store", restored.getPreferenceName());
        assertTrue(restored.shouldWaitForCompletion());
        assertTrue(restored.isResumable());
        assertEquals(90_000, restored.getCompletionTimeoutMillis());
        assertEquals(120_000, restored.getUploadTimeoutMillis());
        assertEquals(1, restored.getFiles().size());
        assertEquals(file.getAbsolutePath(), restored.getFiles().get(0).getPath());
        assertNotNull(restored.getParams());
    }

    @Test
    public void workRequestUsesNetworkConstraint() throws Exception {
        File file = temporaryFolder.newFile("upload.bin");
        AndroidAssemblyWorkConfig config = AndroidAssemblyWorkConfig.newBuilder("key", "secret")
                .addFile(file, "file")
                .build();

        OneTimeWorkRequest request = config.toWorkRequest();
        Constraints constraints = request.getWorkSpec().constraints;
        assertEquals(NetworkType.CONNECTED, constraints.getRequiredNetworkType());
        AndroidAssemblyWorkConfig reread = AndroidAssemblyWorkConfig.fromInputData(request.getWorkSpec().input);
        assertEquals("key", reread.getAuthKey());
    }

    @Test
    public void signatureProviderRoundTrip() throws Exception {
        File file = temporaryFolder.newFile("upload.bin");
        AndroidAssemblyWorkConfig config = AndroidAssemblyWorkConfig.newBuilder("key")
                .signatureProvider("https://example.com/sign")
                .signatureProviderMethod("post")
                .addSignatureProviderHeader("Authorization", "Bearer token")
                .paramsJson("{\"steps\":{\"resize\":{\"robot\":\"/image/resize\"}}}")
                .addFile(file, "file")
                .build();

        JSONObject json = config.toJson();
        AndroidAssemblyWorkConfig restored = AndroidAssemblyWorkConfig.fromJson(json);

        assertEquals("key", restored.getAuthKey());
        assertEquals("https://example.com/sign", restored.getSignatureProviderUrl());
        assertEquals("POST", restored.getSignatureProviderMethod());
        assertEquals("Bearer token", restored.getSignatureProviderHeaders().get("Authorization"));
        assertEquals(1, restored.getFiles().size());
        assertEquals(file.getAbsolutePath(), restored.getFiles().get(0).getPath());
    }

    @Test
    public void allowsRemoteOnlyAssemblies() throws Exception {
        AndroidAssemblyWorkConfig config = AndroidAssemblyWorkConfig.newBuilder("key", "secret")
                .paramsJson("{\"steps\":{\"import\":{\"robot\":\"/http/import\",\"url\":\"https://example.com/file.jpg\"}}}")
                .build();

        assertTrue(config.getFiles().isEmpty());
        assertNotNull(config.getParams());
    }
}
