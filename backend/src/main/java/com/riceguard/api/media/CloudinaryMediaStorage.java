package com.riceguard.api.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.riceguard.api.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CloudinaryMediaStorage implements MediaStorage {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryMediaStorage.class);

    private final Cloudinary cloudinary;

    public CloudinaryMediaStorage(String cloudinaryUrl) {
        if (cloudinaryUrl == null || cloudinaryUrl.isBlank()) {
            throw new IllegalStateException("Thiếu CLOUDINARY_URL (dạng cloudinary://API_KEY:API_SECRET@CLOUD_NAME)");
        }
        this.cloudinary = new Cloudinary(cloudinaryUrl);
        this.cloudinary.config.secure = true;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ImageRef upload(byte[] data, String folder) {
        try {
            Map result = cloudinary.uploader().upload(data, ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "image",
                    "unique_filename", true,
                    "overwrite", false,
                    // Giới hạn kích thước lưu trữ: cạnh lớn nhất 1600px, nén tự động.
                    "transformation", new Transformation().width(1600).height(1600).crop("limit").quality("auto")));
            return new ImageRef((String) result.get("secure_url"), (String) result.get("public_id"),
                    asInt(result.get("width")), asInt(result.get("height")), asLong(result.get("bytes")));
        } catch (IOException | RuntimeException e) {
            log.error("Cloudinary upload thất bại", e);
            throw ApiException.unavailable("Không tải được ảnh lên kho lưu trữ, vui lòng thử lại");
        }
    }

    @Override
    public void delete(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("invalidate", true, "resource_type", "image"));
        } catch (IOException | RuntimeException e) {
            log.warn("Không xóa được ảnh {}: {}", publicId, e.getMessage());
        }
    }

    @Override
    public void deleteByPrefix(String prefix) {
        try {
            cloudinary.api().deleteResourcesByPrefix(prefix, ObjectUtils.asMap("invalidate", true));
        } catch (Exception e) {
            log.warn("Không xóa được thư mục ảnh {}: {}", prefix, e.getMessage());
        }
    }

    @Override
    public boolean ownsUrl(String url) {
        return url != null && url.startsWith("https://res.cloudinary.com/" + cloudinary.config.cloudName + "/");
    }

    @Override
    public String variant(String url, int maxSide) {
        if (url == null) return null;
        int i = url.indexOf("/upload/");
        if (i < 0) return url;
        String t = "c_limit,w_" + maxSide + ",h_" + maxSide + ",q_auto,f_auto";
        return url.substring(0, i + 8) + t + "/" + url.substring(i + 8);
    }

    @Override
    public Map<String, Object> signUpload(String folder) {
        long ts = System.currentTimeMillis() / 1000;
        Map<String, Object> params = new HashMap<>();
        params.put("folder", folder);
        params.put("timestamp", ts);
        String signature = cloudinary.apiSignRequest(params, cloudinary.config.apiSecret, 1);
        Map<String, Object> out = new HashMap<>(params);
        out.put("signature", signature);
        out.put("apiKey", cloudinary.config.apiKey);
        out.put("cloudName", cloudinary.config.cloudName);
        out.put("uploadUrl", "https://api.cloudinary.com/v1_1/" + cloudinary.config.cloudName + "/image/upload");
        return out;
    }

    private static Integer asInt(Object o) { return o instanceof Number n ? n.intValue() : null; }
    private static Long asLong(Object o) { return o instanceof Number n ? n.longValue() : null; }
}
