[![CI](https://github.com/transloadit/android-sdk/actions/workflows/CI.yml/badge.svg?branch=master)](https://github.com/transloadit/android-sdk/actions/workflows/CI.yml)
# android-sdk 
An **Android** Integration for [Transloadit](https://transloadit.com)'s file uploading and encoding service

## Intro

[Transloadit](https://transloadit.com) is a service that helps you handle file uploads, resize, crop and watermark your images, make GIFs, transcode your videos, extract thumbnails, generate audio waveforms, and so much more. In short, [Transloadit](https://transloadit.com) is the Swiss Army Knife for your files.

This is an **Android** SDK to make it easy to talk to the [Transloadit](https://transloadit.com) REST API.

## Install

The JARs can be downloaded manually from [Maven Central](https://search.maven.org/artifact/com.transloadit.android.sdk/transloadit-android).

**Gradle:**

```groovy
implementation 'com.transloadit.android.sdk:transloadit-android:0.0.5'
```

**Maven:**

```xml
<dependency>
  <groupId>com.transloadit.android.sdk</groupId>
  <artifactId>transloadit-android</artifactId>
  <version>0.0.5</version>
</dependency>
```

<a name="user-content-android-sdk-usage">

## Usage

All interactions with the SDK begin with the `com.transloadit.android.sdk.Transloadit` class.

<a name="user-content-android-sdk-create-an-assembly">

### Create an Assembly

To create an assembly, you use the `newAssembly` method.

To use this functionality in it's full glory, you need implement the `AssemblyProgressListener` 
interface.

```java

public class MyAssemblyProgressListener  implements AssemblyProgressListener {
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
        
        AssemblyProgressListener listener = new MyAssemblyProgressListener();

        AndroidTransloadit transloadit = new AndroidTransloadit("key", "secret");
        AndroidAsyncAssembly assembly = transloadit.newAssembly(listener);
        assembly.addFile(new File("path/to/file.jpg"), "file");
        
        Map<String, Object> stepOptions = new HashMap<>();
        stepOptions.put("width", 75);
        stepOptions.put("height", 75);
        stepOptions.put("resize_strategy", "pad");
        assembly.addStep("resize", "/image/resize", stepOptions);

        assembly.save();
    }
}

```

<a name="user-content-android-sdk-example">

## Example

For fully working examples take a look at [examples/](https://github.com/transloadit/android-sdk/tree/master/examples).

<a name="user-content-android-sdk-documentation">

## Documentation

See [Javadoc](https://javadoc.io/doc/com.transloadit.android.sdk/transloadit-android/latest/index.html) for full API documentation.

## License

[The MIT License](LICENSE).
