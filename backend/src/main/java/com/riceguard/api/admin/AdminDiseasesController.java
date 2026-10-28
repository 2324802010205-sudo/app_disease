package com.riceguard.api.admin;

import com.riceguard.api.common.ApiException;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Refs;
import com.riceguard.api.disease.Disease;
import com.riceguard.api.disease.DiseaseService;
import com.riceguard.api.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/diseases")
public class AdminDiseasesController {
    public record Draft(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 120) String scientificName,
            @NotBlank @Size(max = 4000) String description,
            @NotBlank String part,
            @NotBlank String cause,
            boolean common,
            List<String> symptoms,
            List<String> conditions,
            List<String> prevention,
            List<String> treatment) {}

    private final Fs fs;
    private final Refs refs;
    private final DiseaseService diseases;
    private final AdminAuditService audit;

    public AdminDiseasesController(Fs fs, Refs refs, DiseaseService diseases, AdminAuditService audit) {
        this.fs = fs; this.refs = refs; this.diseases = diseases; this.audit = audit;
    }

    @GetMapping
    public List<Disease> list() { return diseases.all(); }

    @GetMapping("/{id}")
    public Disease detail(@PathVariable String id) { return diseases.require(id); }

    @PostMapping("/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    @CacheEvict(value = "diseases", allEntries = true)
    public Disease create(@AuthenticationPrincipal AuthUser admin,
                          @PathVariable @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,59}") String id,
                          @Valid @RequestBody Draft draft) {
        if (!id.matches("[a-z0-9][a-z0-9-]{1,59}")) throw ApiException.badRequest("Mã bệnh không hợp lệ");
        if (fs.exists(refs.diseases().document(id))) throw ApiException.conflict("Mã bệnh đã tồn tại");
        Disease disease = fromDraft(id, 0, draft, null);
        fs.set(refs.diseases().document(id), disease);
        audit.record(admin.uid(), "DISEASE_CREATE", "DISEASE", id, "");
        return disease;
    }

    @PutMapping("/{id}")
    @CacheEvict(value = "diseases", allEntries = true)
    public Disease update(@AuthenticationPrincipal AuthUser admin, @PathVariable String id,
                          @Valid @RequestBody Draft draft) {
        Disease old = diseases.require(id);
        Disease disease = fromDraft(id, old.version(), draft, old);
        fs.set(refs.diseases().document(id), disease);
        audit.record(admin.uid(), "DISEASE_UPDATE", "DISEASE", id, "");
        return disease;
    }

    @PutMapping("/{id}/visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @CacheEvict(value = "diseases", allEntries = true)
    public void visibility(@AuthenticationPrincipal AuthUser admin, @PathVariable String id,
                           @RequestBody Map<String, Boolean> body) {
        Boolean hidden = body.get("hidden");
        if (hidden == null) throw ApiException.badRequest("Thiếu trạng thái hidden");
        diseases.require(id);
        Fs.await(refs.diseases().document(id).update("hidden", hidden));
        audit.record(admin.uid(), hidden ? "DISEASE_HIDE" : "DISEASE_SHOW", "DISEASE", id, "");
    }

    private static Disease fromDraft(String id, int version, Draft draft, Disease old) {
        return new Disease(id, version, draft.name().trim(), draft.scientificName(), draft.description().trim(),
                draft.part(), draft.cause(), draft.common(), list(draft.symptoms()), list(draft.conditions()),
                list(draft.prevention()), list(draft.treatment()), old == null ? null : old.imageUrl(),
                old == null ? null : old.imagePublicId(), old != null && old.hidden(), true);
    }

    private static List<String> list(List<String> input) {
        return input == null ? List.of() : input.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
    }
}
