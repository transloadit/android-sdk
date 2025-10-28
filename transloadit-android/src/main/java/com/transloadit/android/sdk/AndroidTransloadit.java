package com.transloadit.android.sdk;

import android.content.Context;

import androidx.annotation.Nullable;

import com.transloadit.sdk.SignatureProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Android-friendly extension of the core {@link com.transloadit.sdk.Transloadit} client.
 */
public class AndroidTransloadit extends com.transloadit.sdk.Transloadit {

    /**
     * Creates a client using inline credentials and a custom host.
     *
     * @param key Transloadit API key
     * @param secret Transloadit API secret
     * @param duration signature validity duration in seconds
     * @param hostUrl Transloadit API host
     */
    public AndroidTransloadit(String key, @Nullable String secret, long duration, String hostUrl) {
        super(key, secret, duration, hostUrl);
    }

    /**
     * A new instance to transloadit client
     *
     * @param key User's transloadit key
     * @param secret User's transloadit secret
     * @param hostUrl the host url to the transloadit service
     */
    public AndroidTransloadit(String key, String secret, String hostUrl) {
        this(key, secret, 5 * 60, hostUrl);
    }

    /**
     * A new instance to transloadit client
     *
     * @param key User's transloadit key
     * @param secret User's transloadit secret
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

    /**
     * Creates a new {@link AndroidAssembly} that dispatches callbacks on Android threads.
     *
     * @param listener lifecycle listener that receives callbacks
     * @param context Android context used for configuration
     * @return configured {@link AndroidAssembly}
     */
    public AndroidAssembly newAssembly(AndroidAssemblyListener listener, Context context) {
        return new AndroidAssembly(this, listener, context);
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
     *
     * @return version number as string
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
