package com.riceguard.api.admin;

import com.riceguard.api.common.Cursor;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.PageResult;
import com.riceguard.api.common.Paging;
import com.riceguard.api.common.Times;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AdminAuditService {
    public record Entry(String id, String actorId, String action, String targetType, String targetId,
                        String detail, long createdAt) {}

    private final Fs fs;
    private final Paging paging;
    private final Times times;

    public AdminAuditService(Fs fs, Paging paging, Times times) {
        this.fs = fs; this.paging = paging; this.times = times;
    }

    public void record(String actorId, String action, String targetType, String targetId, String detail) {
        var ref = fs.db().collection("adminAudit").document();
        Fs.await(ref.set(Map.of("actorId", actorId, "action", action, "targetType", targetType,
                "targetId", targetId, "detail", detail == null ? "" : detail, "createdAt", times.now())));
    }

    public PageResult<Entry> list(String actorId, String action, String targetType, String targetId, String cursor, Integer limit) {
        return paging.page(fs.db().collection("adminAudit"), cursor, Cursor.limit(limit, 20, 50), Entry.class,
                e -> (actorId == null || actorId.isBlank() || actorId.equals(e.actorId()))
                        && (action == null || action.isBlank() || action.equals(e.action()))
                        && (targetType == null || targetType.isBlank() || targetType.equals(e.targetType()))
                        && (targetId == null || targetId.isBlank() || targetId.equals(e.targetId())));
    }
}
