package com.transloadit.examples;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import io.tus.java.client.ProtocolException;

import com.transloadit.android.sdk.Assembly;
import com.transloadit.android.sdk.AssemblyProgressListener;
import com.transloadit.android.sdk.Transloadit;
import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import org.json.JSONException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity implements AssemblyProgressListener {
    private final int REQUEST_FILE_SELECT = 1;
    private TextView status;
    private Button pauseButton;
    private Button resumeButton;
    private ProgressBar progressBar;
    private Transloadit transloadit;
    private Assembly assembly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        transloadit = new Transloadit("key", "secret");
        assembly = transloadit.newAssembly(this);

        status = (TextView) findViewById(R.id.status);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("*/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select file to upload"), REQUEST_FILE_SELECT);

            }
        });

        pauseButton = (Button) findViewById(R.id.pause_button);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseUpload();
            }
        });

        resumeButton = (Button) findViewById(R.id.resume_button);
        resumeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resumeUpload();
            }
        });
    }

    private void submitAssembly(Uri uri) {
        assembly.addFile("file", uri);
        Map<String, Object> stepOptions = new HashMap<>();
        stepOptions.put("width", 75);
        stepOptions.put("height", 75);
        stepOptions.put("resize_strategy", "pad");
        assembly.addStep("resize", "/image/resize", stepOptions);

        SaveTask saveTask = new SaveTask(this);
        saveTask.execute(true);
    }

    public void setPauseButtonEnabled(boolean enabled) {
        pauseButton.setEnabled(enabled);
        resumeButton.setEnabled(!enabled);
    }

    public void pauseUpload() {
        assembly.pauseUpload();
        setPauseButtonEnabled(false);
    }

    public void resumeUpload() {
        try {
            assembly.resumeUpload();
            setPauseButtonEnabled(true);
        } catch (IOException e) {
            showError(e);
        } catch (ProtocolException e) {
            showError(e);
        }
    }

    @Override
    public void onUploadFinished() {
        setStatus("You Assembly Upload is done and it's now executing");
    }

    @Override
    public void onAssemblyFinished(AssemblyResponse response) {
        try {
            setStatus("Your Assembly is done executing with status: " + response.json().getString("ok"));
        } catch (JSONException e) {
            showError(e);
        }
    }

    @Override
    public void onUploadFailed(Exception exception) {
        showError(exception);
    }

    @Override
    public void onAssemblyStatusUpdateFailed(Exception exception) {
        showError(exception);
    }

    @Override
    public ProgressBar getProgressBar() {
        return progressBar;
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    private void setUploadProgress(int progress) {
        progressBar.setProgress(progress);
    }

    private void showError(Exception e) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Internal error");
        builder.setMessage(e.getMessage());
        AlertDialog dialog = builder.create();
        dialog.show();
        e.printStackTrace();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == REQUEST_FILE_SELECT) {
            Uri uri = data.getData();
            submitAssembly(uri);
        }
    }

    private class SaveTask extends AsyncTask<Boolean, Void, AssemblyResponse> {
        private MainActivity activity;

        public SaveTask(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        protected void onPostExecute(AssemblyResponse response) {
            activity.setStatus("Your assembly is running on " + response.getUrl());
            activity.setPauseButtonEnabled(true);
        }

        @Override
        protected AssemblyResponse doInBackground(Boolean... params) {
            try {
                return assembly.save(params[0]);
            } catch (LocalOperationException e) {
                showError(e);
            } catch (RequestException e) {
                showError(e);
            }

            return null;
        }
    }
}
