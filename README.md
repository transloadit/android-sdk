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

## Example

For fully working examples take a look at [examples/](https://github.com/transloadit/android-sdk/tree/main/examples).

## Development

Run the unit test suite inside Docker to avoid installing the Android toolchain locally:

```bash
./scripts/test-in-docker.sh
```

The script builds a lightweight image with the necessary Android command-line tools, caches Gradle downloads inside `.android-docker/`, and runs `./gradlew test` with the SDK preconfigured. If you use Colima, the script will automatically fall back to `~/.colima/default/docker.sock` when the default Docker socket is unavailable.

## Documentation

See [Javadoc](https://javadoc.io/doc/com.transloadit.android.sdk/transloadit-android/latest/index.html) for full API documentation.

## License

[The MIT License](LICENSE).
