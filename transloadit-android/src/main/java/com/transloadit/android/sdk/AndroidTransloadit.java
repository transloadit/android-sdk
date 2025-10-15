package com.transloadit.android.sdk;


import android.content.Context;

import androidx.annotation.Nullable;

import com.transloadit.sdk.SignatureProvider;
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

    /**
     * A new instance to transloadit client without a secret, using external signature generation.
     *
     * <p>This constructor should be used when you want to generate signatures on your backend
     * server instead of including the secret key in your Android application. This approach
     * significantly improves security by preventing the secret from being extracted from the APK.</p>
     *
     * @param key User's transloadit key
     * @param signatureProvider Provider for generating signatures externally
     * @param duration for how long (in seconds) the request should be valid
     * @param hostUrl the host url to the transloadit service
     */
    public AndroidTransloadit(String key, SignatureProvider signatureProvider, long duration, String hostUrl) {
        super(key, signatureProvider, duration, hostUrl);
    }

    /**
     * A new instance to transloadit client without a secret, using external signature generation.
     *
     * <p>This constructor should be used when you want to generate signatures on your backend
     * server instead of including the secret key in your Android application.</p>
     *
     * @param key User's transloadit key
     * @param signatureProvider Provider for generating signatures externally
     * @param hostUrl the host url to the transloadit service
     */
    public AndroidTransloadit(String key, SignatureProvider signatureProvider, String hostUrl) {
        this(key, signatureProvider, 5 * 60, hostUrl);
    }

    /**
     * A new instance to transloadit client without a secret, using external signature generation.
     *
     * <p>This constructor should be used when you want to generate signatures on your backend
     * server instead of including the secret key in your Android application.</p>
     *
     * @param key User's transloadit key
     * @param signatureProvider Provider for generating signatures externally
     */
    public AndroidTransloadit(String key, SignatureProvider signatureProvider) {
        this(key, signatureProvider, 5 * 60, DEFAULT_HOST_URL);
    }

    public AndroidAsyncAssembly newAssembly(AssemblyProgressListener listener, Context context) {
        return new AndroidAsyncAssembly(this, listener, context);
    }

    /**
     * Internal helper used by tests to inspect the configured API key.
     */
    String getKeyForTesting() {
        return getKeyInternal();
    }

    /**
     * Internal helper used by tests to inspect the configured secret.
     */
    @Nullable
    String getSecretForTesting() {
        return getSecretInternal();
    }

    /**
     * Internal helper used by tests to check whether signing is enabled.
     */
    boolean isSigningEnabledForTesting() {
        return isSigningEnabledInternal();
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
