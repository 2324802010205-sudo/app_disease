package com.riceguard.api.media;

import com.riceguard.api.common.ApiException;
import com.riceguard.api.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
public class MediaService {
    public static final Set<String> KINDS = Set.of("avatar", "field", "scan", "diary", "post", "feedback", "disease");

    private final MediaStorage storage;
    private final AppProperties props;

    public MediaService(MediaStorage storage, AppProperties props) {
        this.storage = storage;
        this.props = props;
    }

    public String folderFor(String uid, String kind) {
        if (!KINDS.contains(kind)) throw ApiException.badRequest("Loại ảnh không hợp lệ: " + kind);
        return props.media().folderPrefix() + "/users/" + uid + "/" + kind;
    }

    public String userRoot(String uid) {
        return props.media().folderPrefix() + "/users/" + uid;
    }

    public byte[] readValidated(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Chưa chọn ảnh");
        if (file.getSize() > props.media().maxBytes()) throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Ảnh quá lớn, tối đa 10 MB mỗi ảnh");
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Không đọc được ảnh tải lên");
        }
        if (ImageSniffer.detect(data) == null) throw ApiException.unsupportedMedia("Chỉ hỗ trợ ảnh JPG, PNG hoặc WebP");
        return data;
    }

    public ImageRef upload(MultipartFile file, String uid, String kind) {
        byte[] data = readValidated(file);
        return storage.upload(data, folderFor(uid, kind));
    }

    public ImageRef uploadBytes(byte[] data, String uid, String kind) {
        if (ImageSniffer.detect(data) == null) throw ApiException.unsupportedMedia("Chỉ hỗ trợ ảnh JPG, PNG hoặc WebP");
        return storage.upload(data, folderFor(uid, kind));
    }

    public String variant(String url, int maxSide) {
        return storage.variant(url, maxSide);
    }

    public void delete(ImageRef ref) {
        if (ref != null && ref.publicId() != null) storage.delete(ref.publicId());
    }

    public void deleteAll(List<ImageRef> refs) {
        if (refs != null) refs.forEach(this::delete);
    }

    public void deleteByPublicId(String publicId) {
        if (publicId != null && !publicId.isBlank()) storage.delete(publicId);
    }

    public void deleteUserFolder(String uid) {
        storage.deleteByPrefix(userRoot(uid));
    }

    /** Chỉ cho phép tham chiếu ảnh nằm trong thư mục của chính người dùng. */
    public void assertOwned(String uid, List<ImageRef> refs) {
        if (refs == null) return;
        String marker = "/users/" + uid + "/";
        for (ImageRef r : refs) {
            if (r == null || r.url() == null || r.publicId() == null || !r.publicId().contains(marker)
                    || !storage.ownsUrl(r.url()) || !r.url().contains(r.publicId())) {
                throw ApiException.forbidden("Ảnh đính kèm không thuộc về bạn");
            }
        }
    }

    public boolean owns(String uid, String publicId) {
        return publicId != null && publicId.contains("/users/" + uid + "/");
    }

    public java.util.Map<String, Object> signUpload(String uid, String kind) {
        return storage.signUpload(folderFor(uid, kind));
    }
}
