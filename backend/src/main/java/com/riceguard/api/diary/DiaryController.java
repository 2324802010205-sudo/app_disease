package com.riceguard.api.diary;

import com.riceguard.api.common.Cursor;
import com.riceguard.api.common.PageResult;
import com.riceguard.api.diary.DiaryService.DiaryRequest;
import com.riceguard.api.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/diary")
@Tag(name = "Nhật ký canh tác", description = "Ảnh đính kèm tải trước qua /media/upload?kind=diary rồi truyền vào images")
public class DiaryController {
    private final DiaryService service;

    public DiaryController(DiaryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<DiaryEntry> list(@AuthenticationPrincipal AuthUser u,
                                       @RequestParam(required = false) String fieldId,
                                       @RequestParam(required = false) Integer days,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(required = false) Integer limit) {
        return service.list(u.uid(), fieldId, days, cursor, Cursor.limit(limit, 20, 50));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiaryEntry create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody DiaryRequest r) {
        return service.create(u.uid(), r);
    }

    @GetMapping("/{id}")
    public DiaryEntry get(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        return service.get(u.uid(), id);
    }

    @PutMapping("/{id}")
    public DiaryEntry update(@AuthenticationPrincipal AuthUser u, @PathVariable String id, @Valid @RequestBody DiaryRequest r) {
        return service.update(u.uid(), id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        service.delete(u.uid(), id);
    }
}
