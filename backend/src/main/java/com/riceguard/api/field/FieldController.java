package com.riceguard.api.field;

import com.riceguard.api.common.PageResult;
import com.riceguard.api.diary.DiaryEntry;
import com.riceguard.api.diary.DiaryService;
import com.riceguard.api.field.FieldDtos.FieldDto;
import com.riceguard.api.field.FieldDtos.FieldRequest;
import com.riceguard.api.media.MediaService;
import com.riceguard.api.scan.Scan;
import com.riceguard.api.scan.ScanService;
import com.riceguard.api.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fields")
@Tag(name = "Ruộng lúa")
public class FieldController {
    public record FieldDetail(FieldDto field, List<Scan> recentScans, List<DiaryEntry> recentDiaries) {}

    private final FieldService service;
    private final ScanService scans;
    private final DiaryService diary;
    private final MediaService media;

    public FieldController(FieldService service, ScanService scans, DiaryService diary, MediaService media) {
        this.service = service;
        this.scans = scans;
        this.diary = diary;
        this.media = media;
    }

    /** sort: updated (mặc định) | name | area. */
    @GetMapping
    public List<FieldDto> list(@AuthenticationPrincipal AuthUser u, @RequestParam(defaultValue = "updated") String sort) {
        return service.list(u.uid(), sort);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FieldDto create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody FieldRequest r) {
        return service.toDto(service.create(u.uid(), r));
    }

    @GetMapping("/{id}")
    public FieldDetail get(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        FieldDto f = service.toDto(service.get(u.uid(), id));
        PageResult<DiaryEntry> d = diary.list(u.uid(), id, null, null, 3);
        return new FieldDetail(f, scans.recentForField(u.uid(), id, 3), d.items());
    }

    @PutMapping("/{id}")
    public FieldDto update(@AuthenticationPrincipal AuthUser u, @PathVariable String id, @Valid @RequestBody FieldRequest r) {
        return service.toDto(service.update(u.uid(), id, r));
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FieldDto photo(@AuthenticationPrincipal AuthUser u, @PathVariable String id, @RequestParam("file") MultipartFile file) {
        service.get(u.uid(), id);
        return service.toDto(service.setPhoto(u.uid(), id, media.upload(file, u.uid(), "field")));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        service.delete(u.uid(), id);
    }
}
