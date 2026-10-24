package com.riceguard.api.common;

import java.util.List;

/** Trang kết quả theo con trỏ; nextCursor == null nghĩa là hết dữ liệu. */
public record PageResult<T>(List<T> items, String nextCursor) {
    public static <T> PageResult<T> of(List<T> items, String nextCursor) {
        return new PageResult<>(items, nextCursor);
    }
}
