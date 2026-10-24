package com.riceguard.api.community;

import com.riceguard.api.media.ImageRef;
import com.riceguard.api.user.UserDtos.PublicUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CommunityDtos {
    private CommunityDtos() {}

    public static final String TOPIC_REGEX = "^(PEST|EXPERIENCE|VARIETY|WEATHER|WATER)$";

    public record PostRequest(
            @NotBlank @Pattern(regexp = TOPIC_REGEX, message = "Chủ đề không hợp lệ") String topic,
            @NotBlank(message = "Nhập tiêu đề bài viết") @Size(max = 100, message = "Tiêu đề tối đa 100 ký tự") String title,
            @NotBlank(message = "Nhập nội dung bài viết") @Size(max = 2000, message = "Nội dung tối đa 2000 ký tự") String content,
            @Size(max = 6, message = "Tối đa 6 ảnh") List<ImageRef> images) {}

    public record DraftRequest(
            @Pattern(regexp = "^(PEST|EXPERIENCE|VARIETY|WEATHER|WATER)?$", message = "Chủ đề không hợp lệ") String topic,
            @Size(max = 100) String title,
            @Size(max = 2000) String content,
            @Size(max = 6) List<ImageRef> images) {}

    public record CommentRequest(
            @NotBlank(message = "Nhập nội dung bình luận") @Size(max = 1000, message = "Bình luận tối đa 1000 ký tự") String text,
            String parentId) {}

    public record ReportRequest(
            @NotBlank @Pattern(regexp = "^(SPAM|MISLEADING|OFFENSIVE|OTHER)$", message = "Lý do báo cáo không hợp lệ") String reason,
            @Size(max = 300) String detail) {}

    public record PostView(String id, PublicUser author, String province, String topic, String title, String content,
                           List<ImageRef> images, long likeCount, long commentCount, boolean liked, boolean saved,
                           boolean mine, String shareUrl, long createdAt, long updatedAt) {}

    /** badge: AUTHOR (tác giả bài viết) | EXPERT (chuyên gia đã xác minh) | null. */
    public record CommentView(String id, String parentId, PublicUser author, String text, long likeCount, boolean liked,
                              boolean mine, String badge, long createdAt, List<CommentView> replies) {}

    public record Draft(String id, String topic, String title, String content, List<ImageRef> images, long createdAt, long updatedAt) {}
}
