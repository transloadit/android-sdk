[![CI](https://github.com/transloadit/android-sdk/actions/workflows/CI.yml/badge.svg?branch=main)](https://github.com/transloadit/android-sdk/actions/workflows/CI.yml)

# android-sdk

An **Android** Integration for [Transloadit](https://transloadit.com)'s file uploading and encoding service

## Intro

[Transloadit](https://transloadit.com) is a service that helps you handle file uploads, resize, crop and watermark your images, make GIFs, transcode your videos, extract thumbnails, generate audio waveforms, and so much more. In short, [Transloadit](https://transloadit.com) is the Swiss Army Knife for your files.

This is an **Android** SDK to make it easy to talk to the [Transloadit](https://transloadit.com) REST API.

## Install

The JARs can be downloaded manually from [Maven Central](https://search.maven.org/artifact/com.transloadit.android.sdk/transloadit-android).

**Gradle:**

```groovy
implementation 'com.transloadit.android.sdk:transloadit-android:1.0.0'
```

**Maven:**

```xml
<dependency>
  <groupId>com.transloadit.android.sdk</groupId>
  <artifactId>transloadit-android</artifactId>
  <version>1.0.0</version>
</dependency>
```

> ℹ️ Signature-based authentication requires `com.transloadit.sdk:transloadit` version **2.2.3** or newer. When developing locally alongside the Java SDK, place both repositories next to each other (`../java-sdk`) and the Gradle build will automatically use the local java-sdk project via dependency substitution.

## Usage

All interactions with the SDK begin with the `com.transloadit.android.sdk.AndroidTransloadit` class.

### Authentication Methods

The SDK supports two authentication methods:

#### 1. Traditional Authentication (with Secret Key)

⚠️ **Security Warning**: Including your secret key in the Android app is a security risk as APK files can be decompiled to extract secrets. Use this method only for development or internal apps.

```java
AndroidTransloadit transloadit = new AndroidTransloadit("YOUR_KEY", "YOUR_SECRET");
```

#### 2. Signature Authentication (Recommended for Production)

For production apps, we strongly recommend using external signature generation. This keeps your secret key on your backend server, preventing it from being extracted from the APK.

```java
// Create a signature provider that fetches signatures from your backend
SignatureProvider signatureProvider = new SignatureProvider() {
    @Override
    public String generateSignature(String paramsJson) throws Exception {
        // Make a request to your backend to sign the parameters
        // This is just an example - implement according to your backend API
        HttpURLConnection conn = (HttpURLConnection) new URL("https://your-backend.com/sign").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.getOutputStream().write(paramsJson.getBytes());

        // Read the signature from your backend's response
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String signature = reader.readLine();
        reader.close();

        return signature; // Should return something like "sha384:..."
    }
};

// Initialize Transloadit with the signature provider
AndroidTransloadit transloadit = new AndroidTransloadit("YOUR_KEY", signatureProvider);
```

Your backend should implement an endpoint that:

1. Validates the request (authentication, rate limiting, etc.)
2. Signs the parameters using your Transloadit secret
3. Returns the signature

Example backend implementation (Node.js):

```javascript
const crypto = require('crypto')

app.post('/sign', authenticate, (req, res) => {
  const paramsJson = req.body
  const signature = crypto
    .createHmac('sha384', process.env.TRANSLOADIT_SECRET)
    .update(Buffer.from(paramsJson, 'utf-8'))
    .digest('hex')

  res.send(`sha384:${signature}`)
})
```

### Create an Assembly

To create an assembly, you use the `newAssembly` method.

To use this functionality in its full glory, implement the `AndroidAssemblyListener`
interface.

```java

public class MyAssemblyListener  implements AndroidAssemblyListener {
    @Override
    public void onUploadFinished() {
        System.out.println("upload finished!!! waiting for execution ...");
    }

    @Override
    public void onUploadProgress(long uploadedBytes, long totalBytes) {
        System.out.println("uploaded: " + uploadedBytes + " of: " + totalBytes);
    }

    @Override
    public void onAssemblyFinished(AssemblyResponse response) {
        System.out.println("Assembly finished with status: " + response.json().getString("ok"));
    }

    @Override
    public void onUploadFailed(Exception exception) {
        System.out.println("upload failed :(");
        exception.printStackTrace();
    }

    @Override
    public void onAssemblyStatusUpdateFailed(Exception exception) {
        System.out.println("unable to fetch status update :(");
        exception.printStackTrace();
    }
}

```

And in your activity you can have something like this

```java
public class MainActivity extends AppCompatActivity {
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        AndroidAssemblyListener listener = new MyAssemblyListener();

        AndroidTransloadit transloadit = new AndroidTransloadit("key", "secret");
        AndroidAssembly assembly = transloadit.newAssembly(listener);
        assembly.addFile(new File("path/to/file.jpg"), "file");

        Map<String, Object> stepOptions = new HashMap<>();
        stepOptions.put("width", 75);
        stepOptions.put("height", 75);
        stepOptions.put("resize_strategy", "pad");
        assembly.addStep("resize", "/image/resize", stepOptions);

        assembly.saveAsync();
    }
}

```

Listener callbacks (`onUploadProgress`, `onAssemblyFinished`, etc.) are dispatched on the Android main thread by default so UI components can be updated safely. To opt out—for example, to process updates off the UI thread—call `useDirectCallbacks()` or install a custom executor:

```java
AndroidAssembly assembly = transloadit.newAssembly(listener);
assembly.useDirectCallbacks(); // run callbacks on the calling thread
// or provide any Executor: assembly.setListenerCallbackExecutor(Executors.newSingleThreadExecutor());
```

## Migration guide (0.x → 1.0)

- Replace `AndroidAsyncAssembly` with the new `AndroidAssembly` wrapper. It returns a `Future` from `saveAsync()` and reports lifecycle events through `AndroidAssemblyListener`.
- Update listeners: `AssemblyProgressListener` and friends were removed. Implement `AndroidAssemblyListener` instead, which exposes upload progress, SSE results, and completion/error hooks.
- Callbacks now fire on the Android main thread by default. If you relied on background delivery, call `assembly.useDirectCallbacks()` or `assembly.setListenerCallbackExecutor(...)`.
- Prefer external signature generation via `new AndroidTransloadit(key, signatureProvider)`. Passing the secret into your APK still works but is no longer recommended for production.
- Tests, CI, and examples now execute against the bundled `chameleon.jpg` fixture and require `result: true` on resize steps so SSE result payloads arrive consistently.
- The SDK depends on `com.transloadit.sdk:transloadit:2.2.4` or newer. Ensure any overrides or local builds are upgraded in lockstep.

## Resumable uploads & WorkManager

- Call `pauseUploadsSafely()` / `resumeUploadsSafely()` on `AndroidAssembly` to control active tus uploads without micromanaging exceptions. Errors are routed to `onAssemblyStatusUpdateFailed` so your listener can surface them to the UI.
- Use unique preference names via `assembly.preferenceName("my_store")` when running multiple concurrent assemblies so tus resume information can be partitioned per job.
- To move uploads into background execution, build a `OneTimeWorkRequest` with `AndroidAssemblyWorkConfig` and enqueue it through WorkManager:

```java
AndroidAssemblyWorkConfig config = AndroidAssemblyWorkConfig
    .newBuilder("TRANSLOADIT_KEY", "TRANSLOADIT_SECRET")
    .paramsJson(paramsJsonString)
    .preferenceName("my_transloadit_store")
    .addFile(new File(context.getCacheDir(), "photo.jpg"), "image")
    .build();

WorkManager.getInstance(context).enqueue(config.toWorkRequest());
```

`AndroidAssemblyUploadWorker` waits for uploads (and, optionally, SSE completion) on a background thread so your app can survive process death or move long-running jobs out of the foreground.

## Example

For fully working examples take a look at [examples/](https://github.com/transloadit/android-sdk/tree/main/examples).

Notably, `examples/src/main/kotlin/com/transloadit/examples/work/WorkManagerSample.kt` demonstrates how to enqueue background uploads using WorkManager and the new `AndroidAssemblyWorkConfig`, both with embedded secrets and with an external signature-provider endpoint. The accompanying unit test (`examples/src/test/kotlin/.../WorkManagerSampleTest.kt`) can be exercised locally via:

```bash
./scripts/test-in-docker.sh :examples:testDebugUnitTest --rerun-tasks
```

Provide the same environment variables (`ANDROID_SDK_E2E`, `TRANSLOADIT_KEY`, `TRANSLOADIT_SECRET`) as the primary E2E flow to run the sample end-to-end against Transloadit.

## Development

Run the unit test suite inside Docker to avoid installing the Android toolchain locally:

```bash
./scripts/test-in-docker.sh
```

The script builds a lightweight image with the necessary Android command-line tools, caches Gradle downloads inside `.android-docker/`, and runs `./gradlew test` with the SDK preconfigured. If you use Colima, the script will automatically fall back to `~/.colima/default/docker.sock` when the default Docker socket is unavailable.

### End-to-end signature provider verification

`transloadit-android/src/test/java/com/transloadit/android/sdk/SignatureProviderE2ETest` drives a real upload against Transloadit to exercise the external signature-provider workflow that the SDK ships for production apps (plus Tus pause/resume, SSE progress/results, etc.). The test is opt-in and executes when `ANDROID_SDK_E2E=true` and both `TRANSLOADIT_KEY` and `TRANSLOADIT_SECRET` are present. To run it locally:

1. Create a `.env` file in the repository root (or export the variables directly) containing:
   ```
   ANDROID_SDK_E2E=1
   TRANSLOADIT_KEY=your-key
   TRANSLOADIT_SECRET=your-secret
   ```
2. Execute the docker harness with the desired Gradle task, for example:
   ```bash
   ./scripts/test-in-docker.sh :transloadit-android:testDebugUnitTest --rerun-tasks
   ```
   The script automatically passes variables from `.env` into the container.

The GitHub Actions workflow (`.github/workflows/CI.yml`) sets the same environment variables from repository secrets, so pull requests and the `main` branch continuously run this end-to-end verification alongside the Java SDK’s signature-parity tests.

## Documentation

See [Javadoc](https://javadoc.io/doc/com.transloadit.android.sdk/transloadit-android/latest/index.html) for full API documentation.

## License

[The MIT License](LICENSE).
