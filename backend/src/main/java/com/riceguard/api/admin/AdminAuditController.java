package com.riceguard.api.admin;

import com.riceguard.api.common.PageResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit")
public class AdminAuditController {
    private final AdminAuditService audit;

    public AdminAuditController(AdminAuditService audit) { this.audit = audit; }

    @GetMapping
    public PageResult<AdminAuditService.Entry> list(@RequestParam(required = false) String actorId,
                                                     @RequestParam(required = false) String action,
                                                     @RequestParam(required = false) String targetType,
                                                     @RequestParam(required = false) String targetId,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(required = false) Integer limit) {
        return audit.list(actorId, action, targetType, targetId, cursor, limit);
    }
}
