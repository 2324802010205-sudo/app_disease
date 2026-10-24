package com.riceguard.api.community;

import com.riceguard.api.common.Cursor;
import com.riceguard.api.common.PageResult;
import com.riceguard.api.community.CommunityDtos.*;
import com.riceguard.api.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Cộng đồng", description = "Bài viết, bình luận, lưu bài, bản nháp, báo cáo")
public class CommunityController {
    private final CommunityService service;

    public CommunityController(CommunityService service) {
        this.service = service;
    }

    /** topic: PEST | EXPERIENCE | VARIETY | WEATHER | WATER (bỏ trống = tất cả). */
    @GetMapping("/posts")
    public PageResult<PostView> feed(@AuthenticationPrincipal AuthUser u,
                                     @RequestParam(required = false) String topic,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(required = false) Integer limit) {
        return service.feed(uid(u), topic, q, cursor, Cursor.limit(limit, 10, 30));
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostView create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody PostRequest r) {
        return service.create(u, r);
    }

    @GetMapping("/posts/{id}")
    public PostView get(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        return service.get(uid(u), id);
    }

    @PutMapping("/posts/{id}")
    public PostView update(@AuthenticationPrincipal AuthUser u, @PathVariable String id, @Valid @RequestBody PostRequest r) {
        return service.update(u.uid(), id, r);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        service.delete(u, id);
    }

    @PutMapping("/posts/{id}/like")
    public Map<String, Object> like(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        return Map.of("liked", true, "likeCount", service.like(u.uid(), id));
    }

    @DeleteMapping("/posts/{id}/like")
    public Map<String, Object> unlike(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        return Map.of("liked", false, "likeCount", service.unlike(u.uid(), id));
    }

    @PutMapping("/posts/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        service.save(u.uid(), id);
    }

    @DeleteMapping("/posts/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        service.unsave(u.uid(), id);
    }

    @PostMapping("/posts/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@AuthenticationPrincipal AuthUser u, @PathVariable String id, @Valid @RequestBody ReportRequest r) {
        service.report(u.uid(), id, null, r);
    }

    /** Khách chưa đăng nhập được phép xem bảng tin nên người dùng có thể là null. */
    private static String uid(AuthUser u) {
        return u == null ? null : u.uid();
    }

    // ---- bình luận

    /** sort: newest | helpful. */
    @GetMapping("/posts/{id}/comments")
    public List<CommentView> comments(@AuthenticationPrincipal AuthUser u, @PathVariable String id,
                                      @RequestParam(defaultValue = "newest") String sort) {
        return service.comments(uid(u), id, sort);
    }

    @PostMapping("/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView comment(@AuthenticationPrincipal AuthUser u, @PathVariable String id, @Valid @RequestBody CommentRequest r) {
        return service.comment(u, id, r);
    }

    @DeleteMapping("/posts/{id}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal AuthUser u, @PathVariable String id, @PathVariable String commentId) {
        service.deleteComment(u, id, commentId);
    }

    @PutMapping("/posts/{id}/comments/{commentId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeComment(@AuthenticationPrincipal AuthUser u, @PathVariable String id, @PathVariable String commentId) {
        service.likeComment(u.uid(), id, commentId, true);
    }

    @DeleteMapping("/posts/{id}/comments/{commentId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeComment(@AuthenticationPrincipal AuthUser u, @PathVariable String id, @PathVariable String commentId) {
        service.likeComment(u.uid(), id, commentId, false);
    }

    @PostMapping("/posts/{id}/comments/{commentId}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportComment(@AuthenticationPrincipal AuthUser u, @PathVariable String id, @PathVariable String commentId,
                              @Valid @RequestBody ReportRequest r) {
        service.report(u.uid(), id, commentId, r);
    }

    // ---- của tôi

    @GetMapping("/users/me/posts")
    public PageResult<PostView> mine(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String cursor,
                                     @RequestParam(required = false) Integer limit) {
        return service.mine(u.uid(), cursor, Cursor.limit(limit, 10, 30));
    }

    @GetMapping("/users/me/saved-posts")
    public PageResult<PostView> saved(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false) Integer limit) {
        return service.saved(u.uid(), cursor, Cursor.limit(limit, 10, 30));
    }

    @GetMapping("/users/me/drafts")
    public PageResult<Draft> drafts(@AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String cursor,
                                    @RequestParam(required = false) Integer limit) {
        return service.drafts(u.uid(), cursor, Cursor.limit(limit, 10, 30));
    }

    @PostMapping("/users/me/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public Draft createDraft(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody DraftRequest r) {
        return service.saveDraft(u.uid(), null, r);
    }

    @PutMapping("/users/me/drafts/{id}")
    public Draft updateDraft(@AuthenticationPrincipal AuthUser u, @PathVariable String id, @Valid @RequestBody DraftRequest r) {
        return service.saveDraft(u.uid(), id, r);
    }

    @DeleteMapping("/users/me/drafts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        service.deleteDraft(u.uid(), id);
    }

    @PostMapping("/users/me/drafts/{id}/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public PostView publishDraft(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        return service.publishDraft(u, id);
    }
}
