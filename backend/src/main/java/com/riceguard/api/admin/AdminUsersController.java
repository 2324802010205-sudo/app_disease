package com.riceguard.api.admin;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.riceguard.api.common.*;
import com.riceguard.api.security.AuthUser;
import com.riceguard.api.user.UserProfile;
import com.riceguard.api.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUsersController {
    public record AdminUser(String id, String email, String fullName, String phone, String province,
                            String district, String role, boolean verifiedExpert, boolean locked,
                            String verifiedBy, Long verifiedAt, String lockedBy, Long lockedAt,
                            long createdAt, long updatedAt) {
        static AdminUser from(UserProfile p) {
            return new AdminUser(p.id(), p.email(), p.fullName(), p.phone(), p.province(), p.district(),
                    p.role(), p.verifiedExpert(), p.locked(), p.verifiedBy(), p.verifiedAt(),
                    p.lockedBy(), p.lockedAt(), p.createdAt(), p.updatedAt());
        }
    }
    public record Detail(AdminUser user, com.riceguard.api.user.UserDtos.Stats stats) {}

    private final Fs fs;
    private final Refs refs;
    private final Paging paging;
    private final Times times;
    private final UserService users;
    private final FirebaseAuth auth;
    private final AdminAuditService audit;

    public AdminUsersController(Fs fs, Refs refs, Paging paging, Times times, UserService users,
                                FirebaseAuth auth, AdminAuditService audit) {
        this.fs = fs; this.refs = refs; this.paging = paging; this.times = times;
        this.users = users; this.auth = auth; this.audit = audit;
    }

    @GetMapping
    public PageResult<AdminUser> list(@RequestParam(defaultValue = "") String q,
                                      @RequestParam(defaultValue = "") String role,
                                      @RequestParam(defaultValue = "") String province,
                                      @RequestParam(required = false) Boolean verified,
                                      @RequestParam(required = false) Boolean locked,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false) Integer limit) {
        String needle = q.toLowerCase(Locale.ROOT).trim();
        PageResult<UserProfile> page = paging.page(refs.users(), cursor, Cursor.limit(limit, 20, 50), UserProfile.class,
                p -> (needle.isBlank() || (p.id() + " " + p.email() + " " + p.fullName()).toLowerCase(Locale.ROOT).contains(needle))
                        && (role.isBlank() || role.equals(p.role()))
                        && (province.isBlank() || province.equalsIgnoreCase(p.province()))
                        && (verified == null || verified == p.verifiedExpert())
                        && (locked == null || locked == p.locked()));
        return PageResult.of(page.items().stream().map(AdminUser::from).toList(), page.nextCursor());
    }

    @GetMapping("/{uid}")
    public Detail detail(@PathVariable String uid) {
        return new Detail(AdminUser.from(users.require(uid)), users.stats(uid));
    }

    @PutMapping("/{uid}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lock(@AuthenticationPrincipal AuthUser admin, @PathVariable String uid,
                     @RequestBody Map<String, Boolean> body) {
        Boolean locked = body.get("locked");
        if (locked == null) throw ApiException.badRequest("Thiếu trạng thái locked");
        if (uid.equals(admin.uid())) throw ApiException.badRequest("Không thể khóa tài khoản của chính mình");
        users.require(uid);
        try {
            UserRecord before = auth.getUser(uid);
            if (locked && before.getCustomClaims() != null && Boolean.TRUE.equals(before.getCustomClaims().get("admin")))
                throw ApiException.badRequest("Không thể khóa tài khoản quản trị");
            auth.updateUser(new UserRecord.UpdateRequest(uid).setDisabled(locked));
            try {
                if (locked) auth.revokeRefreshTokens(uid);
                Fs.await(refs.user(uid).update("locked", locked, "lockedBy", admin.uid(),
                        "lockedAt", times.now(), "updatedAt", times.now()));
            } catch (FirebaseAuthException revokeError) {
                try { auth.updateUser(new UserRecord.UpdateRequest(uid).setDisabled(before.isDisabled())); }
                catch (FirebaseAuthException rollbackError) { revokeError.addSuppressed(rollbackError); }
                throw revokeError;
            } catch (RuntimeException writeError) {
                try { auth.updateUser(new UserRecord.UpdateRequest(uid).setDisabled(before.isDisabled())); }
                catch (FirebaseAuthException rollbackError) { writeError.addSuppressed(rollbackError); }
                throw writeError;
            }
            audit.record(admin.uid(), locked ? "USER_LOCK" : "USER_UNLOCK", "USER", uid, "");
        } catch (FirebaseAuthException e) {
            throw ApiException.unavailable("Không cập nhật được trạng thái tài khoản Firebase");
        }
    }
}
