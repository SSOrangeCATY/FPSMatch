package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class Sha256Digest {
    private Sha256Digest() {
    }

    public static Sha256 of(byte[] value) {
        Objects.requireNonNull(value, "value");
        return finish(newDigest().digest(value));
    }

    public static Sha256 of(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[8192];
        while (true) {
            int count = input.read(buffer);
            if (count < 0) {
                return finish(digest.digest());
            }
            if (count == 0) {
                throw new IOException("SHA-256 input stream made no read progress");
            }
            digest.update(buffer, 0, count);
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Sha256 finish(byte[] digest) {
        return Sha256.parse(HexFormat.of().formatHex(digest));
    }
}
