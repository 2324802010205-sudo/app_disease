package com.example.project_final.model;

import java.util.ArrayList;
import java.util.List;

/** Trang kết quả phân trang theo con trỏ. nextCursor rỗng hoặc null nghĩa là hết dữ liệu. */
public class Page<T> {
    public List<T> items = new ArrayList<>();
    public String nextCursor;
    public long unread; // chỉ có ở danh sách thông báo

    public boolean hasMore() {
        return nextCursor != null && !nextCursor.isEmpty();
    }
}
