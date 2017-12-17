package com.transloadit.android.sdk;

import org.jetbrains.annotations.Nullable;


public class Transloadit extends com.transloadit.sdk.Transloadit {
    public Transloadit(String key, @Nullable String secret, long duration, String hostUrl) {
        super(key, secret, duration, hostUrl);
    }

    /**
     * A new instance to transloadit client
     *
     * @param key User's transloadit key
     * @param secret User's transloadit secret.
     * @param hostUrl the host url to the transloadit service.
     */
    public Transloadit(String key, String secret, String hostUrl) {
        this(key, secret, 5 * 60, hostUrl);
    }

    /**
     * A new instance to transloadit client
     *
     * @param key User's transloadit key
     * @param secret User's transloadit secret.
     */
    public Transloadit(String key, String secret) {
        this(key, secret, 5 * 60, DEFAULT_HOST_URL);
    }

    public Assembly newAssembly(AssemblyProgressListener listener) {
        return new Assembly(this, listener);
    }
}
