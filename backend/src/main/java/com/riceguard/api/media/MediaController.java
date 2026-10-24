package com.riceguard.api.media;

import com.riceguard.api.common.ApiException;
import com.riceguard.api.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media", description = "Tải ảnh lên Cloudinary")
public class MediaController {
    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    /** Tải một ảnh (JPG/PNG/WebP, tối đa 10 MB). kind: avatar | field | scan | diary | post | feedback. */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageRef upload(@AuthenticationPrincipal AuthUser user,
                           @RequestParam("file") MultipartFile file,
                           @RequestParam(defaultValue = "post") String kind) {
        if ("disease".equals(kind)) throw ApiException.forbidden("Không được phép tải loại ảnh này");
        return media.upload(file, user.uid(), kind);
    }

    /** Chữ ký để client tải ảnh thẳng lên Cloudinary, giảm tải cho server. */
    @GetMapping("/upload-signature")
    public Map<String, Object> signature(@AuthenticationPrincipal AuthUser user, @RequestParam(defaultValue = "post") String kind) {
        if ("disease".equals(kind)) throw ApiException.forbidden("Không được phép tải loại ảnh này");
        try {
            return media.signUpload(user.uid(), kind);
        } catch (UnsupportedOperationException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    /** Xóa ảnh đã tải lên nhưng chưa gắn vào dữ liệu nào (ví dụ người dùng bỏ ảnh trước khi đăng). */
    @DeleteMapping("/{*publicId}")
    public Map<String, Boolean> delete(@AuthenticationPrincipal AuthUser user, @PathVariable String publicId) {
        String id = publicId.startsWith("/") ? publicId.substring(1) : publicId;
        if (!media.owns(user.uid(), id)) throw ApiException.forbidden("Ảnh không thuộc về bạn");
        media.deleteByPublicId(id);
        return Map.of("deleted", true);
    }
}
