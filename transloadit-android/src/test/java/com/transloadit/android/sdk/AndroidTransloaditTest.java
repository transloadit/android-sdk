package com.transloadit.android.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.transloadit.sdk.Transloadit;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AndroidTransloaditTest {

    /**
     * Test whether the Instance is created successfully.
     */
    @Test
    public void transloaditInstanceCreation() {
        AndroidTransloadit androidTransloadit = new AndroidTransloadit("key", "secret");
        assertTrue(androidTransloadit instanceof AndroidTransloadit);
        assertTrue(androidTransloadit instanceof Transloadit);
    }

    /**
     * Tets if the version number is obtained by {@link AndroidTransloadit#loadVersionInfo()}.
     */
    @Test
    public void loadVersionInfo() {
        AndroidTransloadit androidTransloadit = new AndroidTransloadit("key", "secret");
        Pattern pattern = Pattern.compile("android-sdk:(([0-9]+)\\.){2}([0-9]+).*, java-sdk:(([0-9]+)\\.){2}([0-9]+).*");
        Matcher matcher = pattern.matcher(androidTransloadit.loadVersionInfo());
        assertTrue(matcher.matches());
    }


}
