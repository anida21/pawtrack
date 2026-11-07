package com.pawtrack.pet.service;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoTypeTest {

    @Test
    void recognisesTheAcceptedFormatsByTheirLeadingBytes() {
        assertThat(PhotoType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01})).contains(PhotoType.JPEG);
        assertThat(PhotoType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}))
            .contains(PhotoType.PNG);
        assertThat(PhotoType.detect(ascii("RIFF1234WEBPVP8 "))).contains(PhotoType.WEBP);
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(PhotoType.detect(new byte[0])).isEmpty();
        assertThat(PhotoType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(PhotoType.detect(ascii("GIF89a"))).isEmpty();
        assertThat(PhotoType.detect(ascii("RIFF1234WAVEfmt "))).isEmpty();   // a WAV file is RIFF too
        assertThat(PhotoType.detect(ascii("<svg xmlns=..."))).isEmpty();
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
