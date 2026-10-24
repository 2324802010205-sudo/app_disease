package com.riceguard.api.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommonUtilTest {
    @Test
    void slugRemovesVietnameseDiacritics() {
        assertEquals("thai-binh", Slug.of("Thái Bình"));
        assertEquals("duong-van-dong", Slug.of("  Đường Văn Đồng  "));
        assertEquals("tp-ho-chi-minh", Slug.of("TP. Hồ Chí Minh"));
        assertEquals("", Slug.of(null));
    }

    @Test
    void foldKeepsSpacesForSearch() {
        assertEquals("dao on la", Slug.fold("Đạo ôn lá"));
    }

    @Test
    void cursorRoundTrips() {
        Cursor c = new Cursor(1792738858899L, "abc:123");
        Cursor back = Cursor.decode(c.encode());
        assertEquals(c, back);
    }

    @Test
    void cursorRejectsGarbageAndAcceptsEmpty() {
        assertNull(Cursor.decode(null));
        assertNull(Cursor.decode(""));
        assertThrows(ApiException.class, () -> Cursor.decode("!!!not-base64!!!"));
    }

    @Test
    void limitIsClamped() {
        assertEquals(20, Cursor.limit(null, 20, 50));
        assertEquals(50, Cursor.limit(999, 20, 50));
        assertEquals(1, Cursor.limit(-5, 20, 50));
    }
}
