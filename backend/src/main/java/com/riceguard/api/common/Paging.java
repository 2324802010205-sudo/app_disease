package com.riceguard.api.common;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Phân trang theo con trỏ trên truy vấn đã sắp theo createdAt giảm dần.
 * Bộ lọc phụ (rủi ro, ruộng, loại thông báo...) áp dụng trong bộ nhớ để không cần index tổ hợp.
 */
@Component
public class Paging {

    private final Fs fs;

    public Paging(Fs fs) {
        this.fs = fs;
    }

    public <T> PageResult<T> page(Query base, String cursor, int limit, Class<T> type, Predicate<T> filter) {
        Query ordered = base.orderBy("createdAt", Query.Direction.DESCENDING).orderBy(FieldPath.documentId(), Query.Direction.DESCENDING);
        Cursor c = Cursor.decode(cursor);
        int batch = Math.max(limit * 2, 20);
        List<T> out = new ArrayList<>();
        Cursor last = null;
        boolean exhausted = false;
        while (!exhausted) {
            Query q = c == null ? ordered : ordered.startAfter(c.createdAt(), c.id());
            List<? extends DocumentSnapshot> docs = Fs.await(q.limit(batch).get()).getDocuments();
            exhausted = docs.size() < batch;
            for (int i = 0; i < docs.size(); i++) {
                DocumentSnapshot d = docs.get(i);
                T item = fs.read(d, type);
                Long created = d.getLong("createdAt");
                c = new Cursor(created == null ? 0 : created, d.getId());
                last = c;
                if (filter == null || filter.test(item)) {
                    out.add(item);
                    if (out.size() == limit) {
                        boolean lastDoc = i == docs.size() - 1;
                        return PageResult.of(out, lastDoc && exhausted ? null : c.encode());
                    }
                }
            }
        }
        return PageResult.of(out, exhausted || last == null ? null : last.encode());
    }

    public <T> PageResult<T> page(Query base, String cursor, int limit, Class<T> type) {
        return page(base, cursor, limit, type, null);
    }
}
