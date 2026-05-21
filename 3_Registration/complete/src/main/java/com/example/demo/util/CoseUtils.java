package com.example.demo.util;

import com.yubico.webauthn.data.ByteArray;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CoseUtils {

    private CoseUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ByteArray sha256(ByteArray data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new ByteArray(digest.digest(data.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static ByteArray sha256(String data) {
        return sha256(new ByteArray(data.getBytes(StandardCharsets.UTF_8)));
    }
}
