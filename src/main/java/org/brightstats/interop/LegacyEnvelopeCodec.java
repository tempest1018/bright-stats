package org.brightstats.interop;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Offline envelope conversion for authorized legacy project interoperability.
 * Keys are supplied by the caller; this component contains no activation logic,
 * embedded vendor keys, target application classes, or network operations.
 */
public final class LegacyEnvelopeCodec {
    private LegacyEnvelopeCodec() {}

    public static String decode(String envelope, byte[] key, boolean urlSafe)
            throws GeneralSecurityException, CharacterCodingException {
        requireKey(key);
        byte[] encrypted = (urlSafe ? Base64.getUrlDecoder() : Base64.getDecoder()).decode(envelope);
        if (encrypted.length == 0 || encrypted.length % 16 != 0) {
            throw new IllegalArgumentException("Legacy envelope must contain complete AES blocks");
        }
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        byte[] plaintext = cipher.doFinal(encrypted);
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(plaintext)).toString();
    }

    public static String encode(String plaintext, byte[] key, boolean urlSafe)
            throws GeneralSecurityException {
        requireKey(key);
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return (urlSafe ? Base64.getUrlEncoder() : Base64.getEncoder()).encodeToString(encrypted);
    }

    private static void requireKey(byte[] key) {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new IllegalArgumentException("Expected an AES key of 16, 24, or 32 bytes");
        }
    }
}
