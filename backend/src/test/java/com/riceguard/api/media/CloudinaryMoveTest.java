package com.riceguard.api.media;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Chạy trên Cloudinary thật (cần CLOUDINARY_URL), nên bỏ qua khi không có biến môi trường này. Tự dọn ảnh thử. */
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_URL", matches = "cloudinary://.+")
class CloudinaryMoveTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Test
    @SuppressWarnings("rawtypes")
    void moveChangesPublicIdAndDisplayedFolderAndAddsLabels() throws Exception {
        String url = System.getenv("CLOUDINARY_URL");
        CloudinaryMediaStorage storage = new CloudinaryMediaStorage(url);
        ImageRef up = storage.upload(PNG, "riceguard/test/users/selftest/scan");
        ImageRef moved = null;
        try {
            moved = storage.move(up.publicId(), "riceguard/test/dataset/dom-nau",
                    Map.of("scan_id", "abc", "ai_label", "dom-nau", "confidence", "0.87"), List.of("dataset", "ai-dom-nau"));
            assertTrue(moved.publicId().startsWith("riceguard/test/dataset/dom-nau/"));
            assertTrue(moved.url().contains(moved.publicId()));

            Map info = new Cloudinary(url).api().resource(moved.publicId(), Map.of());
            assertEquals("riceguard/test/dataset/dom-nau", info.get("asset_folder"));
            assertTrue(((List<?>) info.get("tags")).contains("ai-dom-nau"));
            assertEquals("dom-nau", ((Map) ((Map) info.get("context")).get("custom")).get("ai_label"));
        } finally {
            storage.delete(moved != null ? moved.publicId() : up.publicId());
        }
    }
}
