package com.transloadit.android.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


import android.content.Context;

import com.transloadit.sdk.async.AssemblyProgressListener;
import com.transloadit.sdk.response.AssemblyResponse;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import io.tus.java.client.TusURLMemoryStore;

public class AndroidAsyncAssemblyTest {
    public final int PORT = 9040;
    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this, true, PORT);

    private MockServerClient mockServerClient;
    private boolean uploadFinished;
    private boolean assemblyFinished;
    private long totalUploaded;
    private Exception statusUpdateError;
    private Exception uploadError;


    @Test
    public void testSave() throws Exception {
        // for assembly creation
        mockServerClient.when(HttpRequest.request()
                .withPath("/assemblies/76fe5df1c93a0a530f3e583805cf98b4")
                .withMethod("POST"))
                .respond(HttpResponse.response().withBody(getJson("assembly.json")));

        // for assembly status check
        mockServerClient.when(HttpRequest.request()
                .withPath("/assemblies/76fe5df1c93a0a530f3e583805cf98b4").withMethod("GET"))
                .respond(HttpResponse.response().withBody(getJson("assembly.json")));

        AndroidTransloadit transloadit = new AndroidTransloadit("KEY", "SECRET", "http://localhost:" + PORT);
        AssemblyProgressListener listener = new Listener();
        AndroidAsyncAssembly assembly = new MockAsyncAssembly(transloadit, listener, Mockito.mock(Context.class));
        assembly.setAssemblyId("76fe5df1c93a0a530f3e583805cf98b4");
        assembly.setTusURLStore(new TusURLMemoryStore());
        assembly.addFile(new File(getClass().getClassLoader().getResource("assembly.json").getFile()), "file_name");
        AssemblyResponse resumableAssembly = assembly.save();
        assertEquals(resumableAssembly.json().get("assembly_id"), "76fe5df1c93a0a530f3e583805cf98b4");
        assertTrue(uploadFinished);
        assertTrue(assemblyFinished);
        assertEquals(1077, totalUploaded);
        assertNull(statusUpdateError);
        assertNull(uploadError);
    }

    class Listener implements AssemblyProgressListener {
        @Override
        public void onUploadFinished() {
            uploadFinished = true;
        }

        @Override
        public void onUploadProgress(long uploadedBytes, long totalBytes) {
            totalUploaded = uploadedBytes;
        }

        @Override
        public void onAssemblyFinished(AssemblyResponse response) {
            assemblyFinished = true;
        }

        @Override
        public void onUploadFailed(Exception exception) {
            uploadError = exception;
        }

        @Override
        public void onAssemblyStatusUpdateFailed(Exception exception) {
            statusUpdateError = exception;
        }
    }

    private String getJson (String name) throws IOException {
        String filePath = getClass().getClassLoader().getResource(name).getFile();

        BufferedReader br = new BufferedReader(new FileReader(filePath));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line).append("\n");
            line = br.readLine();
        }

        return sb.toString();
    }
}