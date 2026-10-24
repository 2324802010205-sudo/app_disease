package com.riceguard.api.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {}

    public record UserDto(String id, String email, String fullName, String phone, String province, String district,
                          String role, String bio, String avatarUrl, boolean verifiedExpert, boolean admin,
                          UserProfile.Settings settings, Stats stats, long createdAt) {}

    public record Stats(long scans, long fields, long unreadAlerts) {}

    /** Thông tin công khai hiển thị trên bài viết, bình luận. */
    public record PublicUser(String id, String name, String avatarUrl, String role, boolean verifiedExpert, String province) {}

    public record UpdateProfileRequest(
            @NotBlank(message = "Họ và tên không được để trống") @Size(max = 80, message = "Họ và tên tối đa 80 ký tự") String fullName,
            @Pattern(regexp = "^$|^[0-9+ ()-]{8,20}$", message = "Số điện thoại không hợp lệ") String phone,
            @Size(max = 60) String province,
            @Size(max = 60) String district,
            @Pattern(regexp = "^(USER|FARMER|EXPERT)?$", message = "Vai trò không hợp lệ") String role,
            @Size(max = 300, message = "Giới thiệu tối đa 300 ký tự") String bio) {}

    public record UpdateSettingsRequest(
            @Pattern(regexp = "^(SYSTEM|LIGHT|DARK)?$", message = "Giao diện không hợp lệ") String theme,
            @Pattern(regexp = "^(vi|en)?$", message = "Ngôn ngữ không hợp lệ") String language,
            Boolean notifyAlert, Boolean notifyReminder, Boolean notifyCommunity) {}

    public record DeviceRequest(@NotBlank @Size(max = 4096) String token) {}
}
