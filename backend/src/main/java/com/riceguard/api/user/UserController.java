package com.riceguard.api.user;

import com.riceguard.api.media.MediaService;
import com.riceguard.api.security.AuthUser;
import com.riceguard.api.user.UserDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Hồ sơ", description = "Hồ sơ cá nhân, cài đặt, thiết bị nhận thông báo")
public class UserController {
    private final UserService users;
    private final MediaService media;

    public UserController(UserService users, MediaService media) {
        this.users = users;
        this.media = media;
    }

    @GetMapping
    public UserDto me(@AuthenticationPrincipal AuthUser u) {
        return users.me(u);
    }

    @PutMapping
    public UserDto update(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody UpdateProfileRequest r) {
        return users.update(u, r);
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserDto avatar(@AuthenticationPrincipal AuthUser u, @RequestParam("file") MultipartFile file) {
        return users.setAvatar(u, media.upload(file, u.uid(), "avatar"));
    }

    @PutMapping("/settings")
    public UserDto settings(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody UpdateSettingsRequest r) {
        return users.updateSettings(u, r);
    }

    /** Đăng ký token FCM của thiết bị để nhận thông báo đẩy. */
    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addDevice(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody DeviceRequest r) {
        users.addDevice(u, r.token());
    }

    @DeleteMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDevice(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody DeviceRequest r) {
        users.removeDevice(u.uid(), r.token());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser u) {
        users.deleteAccount(u);
    }
}
