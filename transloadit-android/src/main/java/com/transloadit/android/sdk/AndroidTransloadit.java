package com.transloadit.android.sdk;

import android.app.Activity;

import androidx.annotation.Nullable;

import com.transloadit.sdk.async.AssemblyProgressListener;


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


public class AndroidTransloadit extends com.transloadit.sdk.Transloadit {
    public AndroidTransloadit(String key, @Nullable String secret, long duration, String hostUrl) {
        super(key, secret, duration, hostUrl);
    }

    /**
     * A new instance to transloadit client
     *
     * @param key User's transloadit key
     * @param secret User's transloadit secret.
     * @param hostUrl the host url to the transloadit service.
     */
    public AndroidTransloadit(String key, String secret, String hostUrl) {
        this(key, secret, 5 * 60, hostUrl);
    }

    /**
     * A new instance to transloadit client
     *
     * @param key User's transloadit key
     * @param secret User's transloadit secret.
     */
    public AndroidTransloadit(String key, String secret) {
        this(key, secret, 5 * 60, DEFAULT_HOST_URL);
    }

    public AndroidAsyncAssembly newAssembly(AssemblyProgressListener listener, Activity activity) {
        return new AndroidAsyncAssembly(this, listener, activity);
    }

    /**
     * Determines the current version number of the SDK. This method is called within the constructor
     * of the parent class.
     * @return Version Number as String
     */
    @Override
    protected String loadVersionInfo() {
        String androidSDKVersion = "";
        String javaSDKVersion = super.loadVersionInfo();
        Properties prop = new Properties();
        String sdkName = "android-sdk";
        String version;

        InputStream in = this.getClass().getResourceAsStream("/android-sdk-version/version.properties");
        try {
            prop.load(in);
            version = String.format("%s:%s",sdkName, prop.getProperty("version").replace("\"", ""));
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NullPointerException npe) {
            version = String.format("%s:%s",sdkName, "0.0.0");
        }

        return String.format("%s, %s", version, javaSDKVersion);
    }

}
