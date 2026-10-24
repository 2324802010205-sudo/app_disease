package com.riceguard.api.media;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageSnifferTest {
    @Test
    void detectsJpegPngWebp() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        byte[] webp = "RIFF....WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
        assertEquals("image/jpeg", ImageSniffer.detect(jpeg));
        assertEquals("image/png", ImageSniffer.detect(png));
        assertEquals("image/webp", ImageSniffer.detect(webp));
    }

    @Test
    void rejectsOtherContent() {
        assertNull(ImageSniffer.detect("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageSniffer.detect("GIF89a......".getBytes(StandardCharsets.US_ASCII)));
        assertNull(ImageSniffer.detect(new byte[3]));
        assertNull(ImageSniffer.detect(null));
    }

    @Test
    void cloudinaryVariantInsertsTransformation() {
        var storage = new CloudinaryMediaStorage("cloudinary://123456789012345:secret@democloud");
        assertEquals("https://res.cloudinary.com/democloud/image/upload/c_limit,w_480,h_480,q_auto,f_auto/v1/riceguard/a.jpg",
                storage.variant("https://res.cloudinary.com/democloud/image/upload/v1/riceguard/a.jpg", 480));
        assertEquals(true, storage.ownsUrl("https://res.cloudinary.com/democloud/image/upload/v1/x.jpg"));
        assertEquals(false, storage.ownsUrl("https://evil.example/democloud/image/upload/v1/x.jpg"));
        assertEquals(false, storage.ownsUrl("https://res.cloudinary.com/othercloud/image/upload/v1/x.jpg"));
    }

    @Test
    void cloudinarySignatureIsStableAndContainsFolder() {
        var storage = new CloudinaryMediaStorage("cloudinary://123456789012345:secret@democloud");
        var sig = storage.signUpload("riceguard/dev/users/u1/post");
        assertEquals("democloud", sig.get("cloudName"));
        assertEquals("riceguard/dev/users/u1/post", sig.get("folder"));
        assertEquals(40, ((String) sig.get("signature")).length()); // SHA-1 hex
    }
}
