package com.riceguard.api.notification;

import com.riceguard.api.common.Cursor;
import com.riceguard.api.common.PageResult;
import com.riceguard.api.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Thông báo")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** filter: all | unread | alert | reminder | community | tip */
    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AuthUser u,
                                    @RequestParam(defaultValue = "all") String filter,
                                    @RequestParam(required = false) String cursor,
                                    @RequestParam(required = false) Integer limit) {
        PageResult<Notification> page = service.list(u.uid(), filter, cursor, Cursor.limit(limit, 20, 50));
        return Map.of("items", page.items(), "nextCursor", page.nextCursor() == null ? "" : page.nextCursor(),
                "unread", service.unreadCount(u.uid()));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal AuthUser u) {
        return service.counts(u.uid());
    }

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        service.markRead(u.uid(), id);
    }

    @PutMapping("/read-all")
    public Map<String, Integer> readAll(@AuthenticationPrincipal AuthUser u) {
        return Map.of("updated", service.markAllRead(u.uid()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        service.delete(u.uid(), id);
    }
}
