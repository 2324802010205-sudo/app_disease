package com.riceguard.api.media;

/** Ảnh đã lưu trên kho ảnh; client lưu url để hiển thị, publicId để server xóa khi cần. */
public record ImageRef(String url, String publicId, Integer width, Integer height, Long bytes) {
    public ImageRef(String url, String publicId) {
        this(url, publicId, null, null, null);
    }
}
