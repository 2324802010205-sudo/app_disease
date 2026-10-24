package com.riceguard.api.media;

import java.util.Map;

/** Kho lưu ảnh (Cloudinary khi chạy thật, thư mục cục bộ khi phát triển). */
public interface MediaStorage {

    ImageRef upload(byte[] data, String folder);

    void delete(String publicId);

    void deleteByPrefix(String prefix);

    /** URL có thuộc kho ảnh của hệ thống không (chặn client gắn URL tùy ý vào bài viết/nhật ký). */
    boolean ownsUrl(String url);

    /** URL ảnh thu nhỏ/nén cho danh sách và cho AI; chiều dài cạnh lớn nhất = maxSide. */
    String variant(String url, int maxSide);

    /** Tham số ký để client tải thẳng ảnh lên kho (không đi qua server). */
    default Map<String, Object> signUpload(String folder) {
        throw new UnsupportedOperationException("Kho ảnh hiện tại không hỗ trợ tải trực tiếp");
    }
}
