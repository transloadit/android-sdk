# android-sdk
An **Android** Integration for [Transloadit](https://transloadit.com)'s file uploading and encoding service

## Intro

[Transloadit](https://transloadit.com) is a service that helps you handle file uploads, resize, crop and watermark your images, make GIFs, transcode your videos, extract thumbnails, generate audio waveforms, and so much more. In short, [Transloadit](https://transloadit.com) is the Swiss Army Knife for your files.

This is an **Android** SDK to make it easy to talk to the [Transloadit](https://transloadit.com) REST API.

## Install

The JARs can be downloaded manually from our [Bintray project](https://bintray.com/transloadit/maven/transloadit/view#files),
or can be installed from the Maven and Jcenter repositories.

**Gradle:**

```groovy
compile 'com.transloadit.android.sdk:transloadit:0.0.1'
```

**Maven:**

```xml
<dependency>
  <groupId>com.transloadit.android.sdk</groupId>
  <artifactId>transloadit</artifactId>
  <version>0.0.1</version>
</dependency>
```

## Usage

All interactions with the SDK begin with the `com.transloadit.android.sdk.Transloadit` class.

### Create an Assembly

To create an assembly, you use the `newAssembly` method.

To use this functionality in it's full glory, you need implement the `AssemblyProgressListener` 
interface.

```java

public class MyAssemblyProgressListener  implements AssemblyProgressListener {
    private Activity activity;
    private ProgressBar progressBar;
    
    public MyAssemblyProgressListener(Activity activity, ProgressBar progressBar) {
        this.activity = activity;
        this.progressBar = progressBar;
    }
    
    @Override
    public void onUploadFinished() {
        System.out.println("You Assembly Upload is done and it's now executing");
    }

    @Override
    public void onAssemblyFinished(AssemblyResponse response) {
        try {
            System.out.println("Your Assembly is done executing with status: " + response.json().getString("ok"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUploadFailed(Exception exception) {
        System.out.print("Assmebly file upload failed with error: " + exception.getMessage());
    }

    @Override
    public void onAssemblyStatusUpdateFailed(Exception exception) {
        System.out.print("Assmebly status update failed with error: " + exception.getMessage());
    }

    @Override
    public ProgressBar getProgressBar() {
        return progressBar;
    }

    @Override
    public Activity getActivity() {
        return activity;
    }
}
    
```

And in your activity you can have something like this

```
public class MainActivity extends AppCompatActivity implements AssemblyProgressListener {
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        
        AssemblyProgressListener listener = new MyAssemblyProgressListener(this, progressBar);

        Transloadit transloadit = new Transloadit("key", "secret");
        Assembly assembly = transloadit.newAssembly(listener);
        assembly.addFile("file", new File("path/to/file.jpg"));
        
        Map<String, Object> stepOptions = new HashMap<>();
        stepOptions.put("width", 75);
        stepOptions.put("height", 75);
        stepOptions.put("resize_strategy", "pad");
        assembly.addStep("resize", "/image/resize", stepOptions);

        assembly.save();
    }

```
