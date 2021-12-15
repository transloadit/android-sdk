package com.transloadit.android.sdk;

import android.app.Activity;
import androidx.annotation.Nullable;

import com.transloadit.sdk.async.AssemblyProgressListener;



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
}
