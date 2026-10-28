package com.riceguard.api.user;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.riceguard.api.common.ApiException;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.media.ImageRef;
import com.riceguard.api.media.MediaService;
import com.riceguard.api.security.AuthUser;
import com.riceguard.api.user.UserDtos.PublicUser;
import com.riceguard.api.user.UserDtos.Stats;
import com.riceguard.api.user.UserDtos.UpdateProfileRequest;
import com.riceguard.api.user.UserDtos.UpdateSettingsRequest;
import com.riceguard.api.user.UserDtos.UserDto;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserService {
    private final Fs fs;
    private final Refs refs;
    private final Times times;
    private final MediaService media;
    private final FirebaseAuth auth;
    private final Cache<String, PublicUser> directory = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).maximumSize(50_000).build();

    public UserService(Fs fs, Refs refs, Times times, MediaService media, FirebaseAuth auth) {
        this.fs = fs;
        this.refs = refs;
        this.times = times;
        this.media = media;
        this.auth = auth;
    }

    /** Lấy hồ sơ; nếu người dùng mới đăng nhập lần đầu (ví dụ qua Firebase SDK) thì tạo hồ sơ mặc định. */
    public UserProfile getOrCreate(AuthUser u) {
        DocumentReference ref = refs.user(u.uid());
        UserProfile p = fs.get(ref, UserProfile.class);
        if (p != null) return p;
        long now = times.now();
        String name = u.name() != null && !u.name().isBlank() ? u.name() : defaultName(u.email());
        UserProfile created = new UserProfile(u.uid(), u.email(), name, null, null, null, "FARMER", null, null, null,
                false, UserProfile.Settings.defaults(), new ArrayList<>(), now, now, false, null, null, null, null);
        fs.set(ref, created);
        return created;
    }

    public boolean exists(String uid) {
        return fs.get(refs.user(uid), UserProfile.class) != null;
    }

    /** Ảnh đại diện lấy từ nhà cung cấp ngoài (Google): chỉ lưu địa chỉ, không có publicId để xóa. */
    public UserProfile setExternalAvatar(String uid, String url) {
        Fs.await(refs.user(uid).update("avatarUrl", url, "updatedAt", times.now()));
        directory.invalidate(uid);
        return require(uid);
    }

    public UserProfile create(String uid, String email, String fullName, String phone, String province, String role) {
        long now = times.now();
        UserProfile p = new UserProfile(uid, email, fullName, phone, province, null,
                role == null ? "FARMER" : role, null, null, null, false, UserProfile.Settings.defaults(), new ArrayList<>(), now, now,
                false, null, null, null, null);
        fs.set(refs.user(uid), p);
        return p;
    }

    public UserProfile require(String uid) {
        return fs.require(refs.user(uid), UserProfile.class, "Không tìm thấy người dùng");
    }

    public UserDto me(AuthUser u) {
        UserProfile p = getOrCreate(u);
        return toDto(p, u.admin(), stats(u.uid()));
    }

    public Stats stats(String uid) {
        long scans = fs.count(refs.scans(uid));
        long fields = fs.count(refs.fields(uid));
        long unread = fs.count(refs.notifications(uid).whereEqualTo("read", false).whereEqualTo("kind", "ALERT"));
        return new Stats(scans, fields, unread);
    }

    public UserDto update(AuthUser u, UpdateProfileRequest r) {
        getOrCreate(u);
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", r.fullName().trim());
        m.put("phone", blankToNull(r.phone()));
        m.put("province", blankToNull(r.province()));
        m.put("district", blankToNull(r.district()));
        if (r.role() != null && !r.role().isBlank()) {
            m.put("role", r.role());
            if (!"EXPERT".equals(r.role())) {
                m.put("verifiedExpert", false);
                m.put("verifiedBy", null);
                m.put("verifiedAt", null);
            }
        }
        m.put("bio", blankToNull(r.bio()));
        m.put("updatedAt", times.now());
        Fs.await(refs.user(u.uid()).update(m));
        directory.invalidate(u.uid());
        syncDisplayName(u.uid(), r.fullName().trim());
        return me(u);
    }

    public UserDto setAvatar(AuthUser u, ImageRef image) {
        UserProfile p = getOrCreate(u);
        Map<String, Object> m = new HashMap<>();
        m.put("avatarUrl", image.url());
        m.put("avatarPublicId", image.publicId());
        m.put("updatedAt", times.now());
        Fs.await(refs.user(u.uid()).update(m));
        media.deleteByPublicId(p.avatarPublicId());
        directory.invalidate(u.uid());
        return me(u);
    }

    public UserDto updateSettings(AuthUser u, UpdateSettingsRequest r) {
        UserProfile.Settings s = getOrCreate(u).settingsOrDefault();
        UserProfile.Settings next = new UserProfile.Settings(
                r.theme() != null && !r.theme().isBlank() ? r.theme() : s.theme(),
                r.language() != null && !r.language().isBlank() ? r.language() : s.language(),
                r.notifyAlert() != null ? r.notifyAlert() : s.notifyAlert(),
                r.notifyReminder() != null ? r.notifyReminder() : s.notifyReminder(),
                r.notifyCommunity() != null ? r.notifyCommunity() : s.notifyCommunity());
        Map<String, Object> m = new HashMap<>();
        m.put("settings", fs.toMap(next));
        m.put("updatedAt", times.now());
        Fs.await(refs.user(u.uid()).update(m));
        return me(u);
    }

    public void addDevice(AuthUser u, String token) {
        getOrCreate(u);
        Fs.await(refs.user(u.uid()).update("fcmTokens", FieldValue.arrayUnion(token)));
    }

    public void removeDevice(String uid, String token) {
        Fs.await(refs.user(uid).update("fcmTokens", FieldValue.arrayRemove(token)));
    }

    /** Xóa tài khoản: dữ liệu Firestore, ảnh Cloudinary và tài khoản Firebase Authentication. */
    public void deleteAccount(AuthUser u) {
        media.deleteUserFolder(u.uid());
        fs.deleteRecursive(refs.user(u.uid()));
        directory.invalidate(u.uid());
        try {
            auth.deleteUser(u.uid());
        } catch (FirebaseAuthException e) {
            throw ApiException.unavailable("Không xóa được tài khoản đăng nhập, vui lòng thử lại");
        }
    }

    public void setVerifiedExpert(String uid, boolean verified) {
        setVerifiedExpert(uid, verified, null);
    }

    public void setVerifiedExpert(String uid, boolean verified, String actorId) {
        UserProfile profile = require(uid);
        if (verified && !"EXPERT".equals(profile.role()))
            throw ApiException.badRequest("Chỉ xác minh tài khoản có vai trò chuyên gia");
        Fs.await(refs.user(uid).update("verifiedExpert", verified, "verifiedBy", actorId,
                "verifiedAt", times.now(), "updatedAt", times.now()));
        directory.invalidate(uid);
    }

    public Map<String, PublicUser> lookup(Set<String> ids) {
        Map<String, PublicUser> out = new HashMap<>();
        List<DocumentReference> missing = new ArrayList<>();
        for (String id : ids) {
            PublicUser cached = directory.getIfPresent(id);
            if (cached != null) out.put(id, cached);
            else missing.add(refs.user(id));
        }
        if (!missing.isEmpty()) {
            List<DocumentSnapshot> snaps = Fs.await(refs.users().getFirestore().getAll(missing.toArray(new DocumentReference[0])));
            for (DocumentSnapshot s : snaps) {
                PublicUser pu = s.exists() ? toPublic(fs.read(s, UserProfile.class)) : new PublicUser(s.getId(), "Người dùng đã xóa", null, "USER", false, null);
                directory.put(s.getId(), pu);
                out.put(s.getId(), pu);
            }
        }
        return out;
    }

    public PublicUser publicOf(String uid) {
        return lookup(Set.of(uid)).get(uid);
    }

    public static PublicUser toPublic(UserProfile p) {
        return new PublicUser(p.id(), p.fullName(), p.avatarUrl(), p.role(), p.verifiedExpert(), p.province());
    }

    public static UserDto toDto(UserProfile p, boolean admin, Stats stats) {
        return new UserDto(p.id(), p.email(), p.fullName(), p.phone(), p.province(), p.district(), p.role(), p.bio(),
                p.avatarUrl(), p.verifiedExpert(), admin, p.settingsOrDefault(), stats, p.createdAt());
    }

    private void syncDisplayName(String uid, String name) {
        try {
            auth.updateUser(new com.google.firebase.auth.UserRecord.UpdateRequest(uid).setDisplayName(name));
        } catch (FirebaseAuthException ignored) {
            // tên hiển thị trong Firebase Auth chỉ để tiện, không bắt buộc
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String defaultName(String email) {
        if (email == null) return "Nông dân";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
