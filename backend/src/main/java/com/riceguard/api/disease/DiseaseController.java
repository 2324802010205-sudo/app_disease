package com.riceguard.api.disease;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.riceguard.api.common.Times;
import com.riceguard.api.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Thư viện bệnh", description = "Danh mục bệnh lúa, bệnh đã lưu")
public class DiseaseController {
    public record DiseaseItem(@JsonUnwrapped Disease disease, boolean saved) {}
    public record DiseaseDetail(@JsonUnwrapped Disease disease, boolean saved, List<Disease> related) {}

    private final DiseaseService service;
    private final Times times;

    public DiseaseController(DiseaseService service, Times times) {
        this.service = service;
        this.times = times;
    }

    /** Công khai. part: LEAF|STEM|GRAIN, cause: FUNGUS|BACTERIA|VIRUS. */
    @GetMapping("/diseases")
    public List<DiseaseItem> list(@AuthenticationPrincipal AuthUser u,
                                  @RequestParam(required = false) String part,
                                  @RequestParam(required = false) String cause,
                                  @RequestParam(required = false) Boolean common,
                                  @RequestParam(required = false) String q) {
        Set<String> saved = u == null ? Set.of() : service.savedIds(u.uid());
        return service.search(part, cause, common, q).stream().map(d -> new DiseaseItem(d, saved.contains(d.id()))).toList();
    }

    @GetMapping("/diseases/{id}")
    public DiseaseDetail detail(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        Disease d = service.requireVisible(id);
        boolean saved = u != null && service.savedIds(u.uid()).contains(id);
        return new DiseaseDetail(d, saved, service.related(d, 4));
    }

    @PutMapping("/diseases/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        service.save(u.uid(), id, times.now());
    }

    @DeleteMapping("/diseases/{id}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@AuthenticationPrincipal AuthUser u, @PathVariable String id) {
        service.unsave(u.uid(), id);
    }

    @GetMapping("/users/me/saved-diseases")
    public List<Disease> saved(@AuthenticationPrincipal AuthUser u) {
        return service.saved(u.uid());
    }
}
