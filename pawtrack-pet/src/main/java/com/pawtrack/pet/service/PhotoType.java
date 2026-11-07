package com.pawtrack.pet.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Accepted photo formats, recognised by their leading bytes rather than by the client's
 * {@code Content-Type}, which is easy to get wrong or fake.
 */
enum PhotoType {

    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    private static final byte[] JPEG_START = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_START = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_TAG = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private final String mediaType;

    PhotoType(String mediaType) {
        this.mediaType = mediaType;
    }

    String mediaType() {
        return mediaType;
    }

    static Optional<PhotoType> detect(byte[] content) {
        if (startsWith(content, 0, JPEG_START)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, PNG_START)) {
            return Optional.of(PNG);
        }
        // RIFF container: "RIFF" <4-byte size> "WEBP"
        if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_TAG)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        return content.length >= offset + prefix.length
            && Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
