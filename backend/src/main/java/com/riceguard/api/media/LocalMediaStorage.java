package com.riceguard.api.media;

import com.riceguard.api.common.ApiException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/** Lưu ảnh vào thư mục máy chủ; chỉ dùng cho phát triển và kiểm thử (MEDIA_PROVIDER=local). */
public class LocalMediaStorage implements MediaStorage {
    private final Path root;
    private final String baseUrl;

    public LocalMediaStorage(String dir, String publicBaseUrl) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.baseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    public Path root() { return root; }

    @Override
    public ImageRef upload(byte[] data, String folder) {
        String publicId = folder + "/" + UUID.randomUUID();
        Path file = resolve(publicId);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, data);
        } catch (IOException e) {
            throw ApiException.unavailable("Không lưu được ảnh");
        }
        return new ImageRef(baseUrl + "/files/" + publicId, publicId, null, null, (long) data.length);
    }

    @Override
    public void delete(String publicId) {
        try {
            Files.deleteIfExists(resolve(publicId));
        } catch (IOException ignored) {
            // bỏ qua: xóa ảnh là best-effort
        }
    }

    @Override
    public void deleteByPrefix(String prefix) {
        Path dir = resolve(prefix);
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // best-effort
        }
    }

    @Override
    public boolean ownsUrl(String url) {
        return url != null && url.startsWith(baseUrl + "/files/");
    }

    @Override
    public String variant(String url, int maxSide) {
        return url;
    }

    private Path resolve(String publicId) {
        Path p = root.resolve(publicId).normalize();
        if (!p.startsWith(root)) throw ApiException.badRequest("Đường dẫn ảnh không hợp lệ");
        return p;
    }
}
