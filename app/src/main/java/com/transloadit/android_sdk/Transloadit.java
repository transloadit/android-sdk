package com.transloadit.android_sdk;

import android.app.Activity;

import com.transloadit.sdk.Assembly;

import org.jetbrains.annotations.Nullable;

/**
 * Created by ifedapo on 13/11/2017.
 */

public class Transloadit extends com.transloadit.sdk.Transloadit {
    public Transloadit(String key, @Nullable String secret, long duration, String hostUrl) {
        super(key, secret, duration, hostUrl);
    }

    public Assembly newAssembly(Activity activity) {
        return new ActivityAssembly(this, activity);
    }
}
