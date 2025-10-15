package com.transloadit.examples;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.transloadit.android.sdk.AndroidAsyncAssembly;
import com.transloadit.android.sdk.AndroidTransloadit;
import com.transloadit.sdk.SignatureProvider;
import com.transloadit.sdk.async.AssemblyProgressListener;
import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Example demonstrating how to use external signature generation
 * for improved security in production Android applications.
 *
 * This approach keeps your Transloadit secret on your backend server
 * instead of embedding it in the APK, preventing extraction through
 * decompilation.
 */
public class SignatureProviderExample {
    private static final String TAG = "SignatureProviderExample";

    // Your backend endpoint that generates signatures
    private static final String BACKEND_SIGN_URL = "https://your-backend.com/api/transloadit/sign";

    // Your Transloadit API key (safe to include in APK)
    private static final String TRANSLOADIT_KEY = "YOUR_TRANSLOADIT_KEY";

    /**
     * Custom SignatureProvider that fetches signatures from your backend
     */
    static class BackendSignatureProvider implements SignatureProvider {
        private final String backendUrl;
        private final String authToken; // Your app's auth token for backend requests

        public BackendSignatureProvider(String backendUrl, String authToken) {
            this.backendUrl = backendUrl;
            this.authToken = authToken;
        }

        @Override
        public String generateSignature(String paramsJson) throws Exception {
            // Make a synchronous HTTP request to your backend
            // In production, you might want to use a more sophisticated HTTP client
            // like OkHttp or Retrofit

            URL url = new URL(backendUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            try {
                // Configure the request
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000); // 10 seconds
                conn.setReadTimeout(10000); // 10 seconds

                // Send the params JSON to your backend
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(paramsJson.getBytes("UTF-8"));
                }

                // Check response code
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Backend returned error: " + responseCode);
                }

                // Read the signature from the response
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }

                // Parse the response (assuming JSON format)
                JSONObject jsonResponse = new JSONObject(response.toString());
                String signature = jsonResponse.getString("signature");

                Log.d(TAG, "Received signature from backend: " + signature.substring(0, 20) + "...");

                return signature;

            } finally {
                conn.disconnect();
            }
        }
    }

    /**
     * Example AssemblyProgressListener implementation
     */
    static class ExampleAssemblyListener implements AssemblyProgressListener {
        @Override
        public void onUploadFinished() {
            Log.i(TAG, "Upload finished! Waiting for assembly to complete...");
        }

        @Override
        public void onUploadProgress(long uploadedBytes, long totalBytes) {
            int progress = (int) ((uploadedBytes * 100) / totalBytes);
            Log.d(TAG, "Upload progress: " + progress + "%");
        }

        @Override
        public void onAssemblyFinished(AssemblyResponse response) {
            try {
                Log.i(TAG, "Assembly completed successfully!");
                Log.i(TAG, "Assembly ID: " + response.getId());
                Log.i(TAG, "Assembly URL: " + response.getUrl());

                // Process results
                if (response.getStepResult("resize") != null) {
                    JSONArray resizeResults = response.getStepResult("resize");
                    Log.i(TAG, "Resize result: " + resizeResults.toString(2));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing assembly response", e);
            }
        }

        @Override
        public void onUploadFailed(Exception exception) {
            Log.e(TAG, "Upload failed", exception);
        }

        @Override
        public void onAssemblyStatusUpdateFailed(Exception exception) {
            Log.e(TAG, "Failed to get assembly status update", exception);
        }
    }

    /**
     * Demonstrates how to use the SignatureProvider with AndroidTransloadit
     */
    public static void createAssemblyWithSignatureProvider(Context context, File imageFile, String authToken) {
        // Create the signature provider
        SignatureProvider signatureProvider = new BackendSignatureProvider(BACKEND_SIGN_URL, authToken);

        // Initialize AndroidTransloadit with the signature provider (no secret needed!)
        AndroidTransloadit transloadit = new AndroidTransloadit(TRANSLOADIT_KEY, signatureProvider);

        // Create an assembly listener
        AssemblyProgressListener listener = new ExampleAssemblyListener();

        // Create a new assembly
        AndroidAsyncAssembly assembly = transloadit.newAssembly(listener, context);

        // Add the file to upload
        assembly.addFile(imageFile, "image");

        // Add a resize step
        Map<String, Object> resizeOptions = new HashMap<>();
        resizeOptions.put("width", 200);
        resizeOptions.put("height", 200);
        resizeOptions.put("resize_strategy", "fit");
        resizeOptions.put("format", "jpg");
        assembly.addStep("resize", "/image/resize", resizeOptions);

        // Save the assembly (this will trigger the upload)
        try {
            assembly.save();
            Log.i(TAG, "Assembly creation started with external signature generation");
        } catch (RequestException | LocalOperationException e) {
            Log.e(TAG, "Failed to create assembly", e);
        }
    }

    /**
     * Alternative: Using AsyncTask for better UI integration
     */
    public static class CreateAssemblyTask extends AsyncTask<File, Integer, AssemblyResponse> {
        private final Context context;
        private final String authToken;
        private final AssemblyTaskListener listener;

        public interface AssemblyTaskListener {
            void onAssemblyCreated(AssemblyResponse response);
            void onAssemblyFailed(Exception error);
            void onProgressUpdate(int progress);
        }

        public CreateAssemblyTask(Context context, String authToken, AssemblyTaskListener listener) {
            this.context = context;
            this.authToken = authToken;
            this.listener = listener;
        }

        @Override
        protected AssemblyResponse doInBackground(File... files) {
            if (files.length == 0) {
                return null;
            }

            try {
                // Create signature provider
                SignatureProvider signatureProvider = new BackendSignatureProvider(BACKEND_SIGN_URL, authToken);

                // Initialize Transloadit
                AndroidTransloadit transloadit = new AndroidTransloadit(TRANSLOADIT_KEY, signatureProvider);

                // Create assembly with progress tracking
                AndroidAsyncAssembly assembly = transloadit.newAssembly(new AssemblyProgressListener() {
                    @Override
                    public void onUploadProgress(long uploadedBytes, long totalBytes) {
                        int progress = (int) ((uploadedBytes * 100) / totalBytes);
                        publishProgress(progress);
                    }

                    @Override
                    public void onUploadFinished() {
                        Log.d(TAG, "Upload completed");
                    }

                    @Override
                    public void onAssemblyFinished(AssemblyResponse response) {
                        // Will be handled in onPostExecute
                    }

                    @Override
                    public void onUploadFailed(Exception exception) {
                        Log.e(TAG, "Upload failed", exception);
                    }

                    @Override
                    public void onAssemblyStatusUpdateFailed(Exception exception) {
                        Log.e(TAG, "Status update failed", exception);
                    }
                }, context);

                // Add file and steps
                assembly.addFile(files[0], "image");

                Map<String, Object> resizeOptions = new HashMap<>();
                resizeOptions.put("width", 200);
                resizeOptions.put("height", 200);
                assembly.addStep("resize", "/image/resize", resizeOptions);

                // Execute assembly
                return assembly.save();

            } catch (Exception e) {
                Log.e(TAG, "Failed to create assembly", e);
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (listener != null && values.length > 0) {
                listener.onProgressUpdate(values[0]);
            }
        }

        @Override
        protected void onPostExecute(AssemblyResponse response) {
            if (listener != null) {
                if (response != null) {
                    listener.onAssemblyCreated(response);
                } else {
                    listener.onAssemblyFailed(new Exception("Failed to create assembly"));
                }
            }
        }
    }
}
