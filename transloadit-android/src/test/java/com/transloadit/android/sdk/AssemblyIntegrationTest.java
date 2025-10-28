package com.transloadit.android.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.transloadit.sdk.Assembly;
import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import org.json.JSONArray;
import org.junit.Assume;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AssemblyIntegrationTest {

    @Test
    public void createAssemblyAndWaitForCompletion() throws Exception {
        String key = System.getenv("TRANSLOADIT_KEY");
        String secret = System.getenv("TRANSLOADIT_SECRET");
        Assume.assumeTrue("TRANSLOADIT_KEY env var required", key != null && !key.isEmpty());
        Assume.assumeTrue("TRANSLOADIT_SECRET env var required", secret != null && !secret.isEmpty());

        AndroidTransloadit transloadit = new AndroidTransloadit(key, secret);
        Assembly assembly = transloadit.newAssembly();

        Map<String, Object> importStep = new HashMap<>();
        importStep.put("url", "https://demos.transloadit.com/inputs/chameleon.jpg");
        assembly.addStep("import", "/http/import", importStep);

        Map<String, Object> resizeStep = new HashMap<>();
        resizeStep.put("use", "import");
        resizeStep.put("width", 64);
        resizeStep.put("height", 64);
        assembly.addStep("resize", "/image/resize", resizeStep);

        AssemblyResponse response = assembly.save(false);
        String assemblyId = response.getId();

        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
        while (!response.isFinished() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5000);
            response = transloadit.getAssembly(assemblyId);
        }

        assertTrue("Assembly did not finish in time", response.isFinished());
        assertEquals("ASSEMBLY_COMPLETED", response.json().optString("ok"));

        JSONArray resizeResult = response.getStepResult("resize");
        assertTrue("Expected resize step results", resizeResult != null && resizeResult.length() > 0);
    }
}
