package com.riceguard.api.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/** Tiện ích đọc/ghi Firestore: ánh xạ document <-> record qua Jackson, chờ kết quả đồng bộ. */
@Component
public class Fs {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final Firestore db;
    private final ObjectMapper mapper;

    public Fs(Firestore db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    public Firestore db() { return db; }

    public static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị gián đoạn khi chờ Firestore", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new IllegalStateException(cause);
        }
    }

    public <T> T read(DocumentSnapshot doc, Class<T> type) {
        Map<String, Object> data = doc.getData() == null ? new HashMap<>() : new HashMap<>(doc.getData());
        data.put("id", doc.getId());
        return mapper.convertValue(data, type);
    }

    public <T> T get(DocumentReference ref, Class<T> type) {
        DocumentSnapshot snap = await(ref.get());
        return snap.exists() ? read(snap, type) : null;
    }

    public <T> T require(DocumentReference ref, Class<T> type, String notFoundMessage) {
        T value = get(ref, type);
        if (value == null) throw ApiException.notFound(notFoundMessage);
        return value;
    }

    public boolean exists(DocumentReference ref) {
        return await(ref.get()).exists();
    }

    /** Chuyển record thành map để ghi; bỏ trường id vì id nằm ở đường dẫn document. */
    public Map<String, Object> toMap(Object value) {
        Map<String, Object> map = new HashMap<>(mapper.convertValue(value, MAP));
        map.remove("id");
        return map;
    }

    public void set(DocumentReference ref, Object value) {
        await(ref.set(toMap(value)));
    }

    public <T> List<T> query(Query q, Class<T> type) {
        QuerySnapshot snap = await(q.get());
        List<T> out = new ArrayList<>(snap.size());
        for (DocumentSnapshot d : snap.getDocuments()) out.add(read(d, type));
        return out;
    }

    public long count(Query q) {
        return await(q.count().get()).getCount();
    }

    /** Xóa document cùng toàn bộ subcollection. */
    public void deleteRecursive(DocumentReference ref) {
        await(db.recursiveDelete(ref));
    }
}
