package com.transloadit.android.sdk;

import static org.junit.Assert.*;

import com.transloadit.sdk.SignatureProvider;
import com.transloadit.sdk.exceptions.LocalOperationException;

import org.junit.Test;

/**
 * Test cases for Android SDK SignatureProvider functionality
 */
public class SignatureProviderTest {

    private static class TestSignatureProvider implements SignatureProvider {
        private String signatureToReturn;
        private String lastParamsReceived;
        private boolean shouldThrowException;

        public TestSignatureProvider(String signatureToReturn) {
            this.signatureToReturn = signatureToReturn;
            this.shouldThrowException = false;
        }

        public void setShouldThrowException(boolean shouldThrow) {
            this.shouldThrowException = shouldThrow;
        }

        @Override
        public String generateSignature(String paramsJson) throws Exception {
            if (shouldThrowException) {
                throw new Exception("Test exception from signature provider");
            }
            this.lastParamsReceived = paramsJson;
            return signatureToReturn;
        }

        public String getLastParamsReceived() {
            return lastParamsReceived;
        }
    }

    /**
     * Test AndroidTransloadit instance creation with SignatureProvider
     */
    @Test
    public void testAndroidTransloaditWithSignatureProvider() {
        TestSignatureProvider provider = new TestSignatureProvider("sha384:test-signature");

        // Test all constructor variants
        AndroidTransloadit t1 = new AndroidTransloadit("test_key", provider);
        assertNotNull(t1);
        assertEquals("test_key", t1.getKeyForTesting());
        assertNull(t1.getSecretForTesting());
        assertEquals(provider, t1.getSignatureProvider());
        assertTrue(t1.isSigningEnabledForTesting());

        AndroidTransloadit t2 = new AndroidTransloadit("test_key", provider, "https://api.example.com");
        assertNotNull(t2);
        assertEquals(provider, t2.getSignatureProvider());

        AndroidTransloadit t3 = new AndroidTransloadit("test_key", provider, 600, "https://api.example.com");
        assertNotNull(t3);
        assertEquals(provider, t3.getSignatureProvider());
    }

    /**
     * Test that AndroidTransloadit still works with traditional secret-based auth
     */
    @Test
    public void testAndroidTransloaditWithSecret() {
        AndroidTransloadit transloadit = new AndroidTransloadit("test_key", "test_secret");

        assertNotNull(transloadit);
        assertEquals("test_key", transloadit.getKeyForTesting());
        assertEquals("test_secret", transloadit.getSecretForTesting());
        assertNull(transloadit.getSignatureProvider());
        assertTrue(transloadit.isSigningEnabledForTesting());
    }

    /**
     * Test setting and getting signature provider
     */
    @Test
    public void testSetSignatureProvider() {
        AndroidTransloadit transloadit = new AndroidTransloadit("test_key", "test_secret");

        // Initially no provider
        assertNull(transloadit.getSignatureProvider());

        // Set a provider
        TestSignatureProvider provider = new TestSignatureProvider("sha384:new-signature");
        transloadit.setSignatureProvider(provider);

        assertEquals(provider, transloadit.getSignatureProvider());
        assertTrue(transloadit.isSigningEnabledForTesting());

        // Remove provider
        transloadit.setSignatureProvider(null);
        assertNull(transloadit.getSignatureProvider());
        assertTrue("Secret-based clients should continue signing", transloadit.isSigningEnabledForTesting());
    }

    /**
     * Test that version info includes both Android and Java SDK versions
     */
    @Test
    public void testVersionInfo() {
        AndroidTransloadit transloadit = new AndroidTransloadit("test_key", "test_secret");
        String versionInfo = transloadit.loadVersionInfo();

        assertNotNull(versionInfo);
        assertTrue(versionInfo.contains("android-sdk:"));
        assertTrue(versionInfo.contains("java-sdk:"));
    }

    /**
     * Test creating AndroidTransloadit without secret (for unsigned requests)
     */
    @Test
    public void testAndroidTransloaditWithoutSecret() {
        AndroidTransloadit transloadit = new AndroidTransloadit("test_key", (String) null, 300, "https://api.example.com");

        assertNotNull(transloadit);
        assertEquals("test_key", transloadit.getKeyForTesting());
        assertNull(transloadit.getSecretForTesting());
        assertNull(transloadit.getSignatureProvider());
        assertFalse(transloadit.isSigningEnabledForTesting());

        // Enabling signing without provider or secret should fail
        try {
            transloadit.setRequestSigning(true);
            fail("Expected LocalOperationException when enabling signing without secret or provider");
        } catch (LocalOperationException expected) {
            // expected
        }
    }
}
