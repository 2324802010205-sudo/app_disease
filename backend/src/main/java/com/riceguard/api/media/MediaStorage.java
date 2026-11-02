package com.riceguard.api.media;

import java.util.List;
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

    /** URL ảnh đưa cho mô hình AI: ít nén nhất có thể để không làm mờ vết bệnh. Mặc định giống {@link #variant}. */
    default String forDiagnosis(String url, int maxSide) {
        return variant(url, maxSide);
    }

    /** Chuyển ảnh sang thư mục khác (vẫn một bản duy nhất) và gắn tag/context để lọc trong kho; trả về tham chiếu mới. */
    default ImageRef move(String publicId, String toFolder, Map<String, String> context, List<String> tags) {
        throw new UnsupportedOperationException("Kho ảnh hiện tại không hỗ trợ chuyển thư mục");
    }

    /** Đổi thư mục hiển thị và nhãn mà không đổi URL ảnh. Kho không phân biệt thư mục hiển thị thì bỏ qua. */
    default void setFolder(String publicId, String folder, Map<String, String> context, List<String> tags) {}

    /** Tham số ký để client tải thẳng ảnh lên kho (không đi qua server). */
    default Map<String, Object> signUpload(String folder) {
        throw new UnsupportedOperationException("Kho ảnh hiện tại không hỗ trợ tải trực tiếp");
    }
}
