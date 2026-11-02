package com.riceguard.api.community;

import com.riceguard.api.community.CommunityDtos.CommentView;
import com.riceguard.api.user.UserDtos.PublicUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommentLiveTest {
    private final CommentLive live = new CommentLive();

    private static CommentView view(String id, String authorId) {
        return new CommentView(id, null, new PublicUser(authorId, "Tên", null, "FARMER", false, null), "nội dung", 0, false, false, null, 1L, List.of());
    }

    @Test
    void firstPollReturnsMarkerImmediately() {
        DeferredResult<CommentLive.Batch> r = live.poll("u1", "p1", 0, 5);
        assertTrue(r.hasResult());
        CommentLive.Batch b = (CommentLive.Batch) r.getResult();
        assertFalse(b.reset());
        assertTrue(b.latest() > 0 && b.comments().isEmpty());
    }

    @Test
    void waitingViewersAreAllWokenByOneComment() {
        long mark = ((CommentLive.Batch) live.poll("a", "p1", 0, 5).getResult()).latest();
        DeferredResult<CommentLive.Batch> a = live.poll("a", "p1", mark, 5);
        DeferredResult<CommentLive.Batch> b = live.poll("b", "p1", mark, 5);
        assertFalse(a.hasResult());
        assertFalse(b.hasResult());

        live.published("p1", view("c1", "a"));

        CommentLive.Batch ra = (CommentLive.Batch) a.getResult();
        CommentLive.Batch rb = (CommentLive.Batch) b.getResult();
        assertEquals(1, ra.comments().size());
        assertTrue(ra.comments().get(0).mine());      // người viết thấy là của mình
        assertFalse(rb.comments().get(0).mine());     // người khác thì không
        assertTrue(ra.latest() > mark);
    }

    @Test
    void eventsBeforeMarkerAreNotRepeated() {
        long mark = ((CommentLive.Batch) live.poll("a", "p1", 0, 5).getResult()).latest();
        live.published("p1", view("c1", "a"));
        CommentLive.Batch first = (CommentLive.Batch) live.poll("a", "p1", mark, 5).getResult();
        assertEquals(1, first.comments().size());
        assertFalse(live.poll("a", "p1", first.latest(), 5).hasResult());   // không có gì mới: phải chờ
    }

    @Test
    void removalsAreDelivered() {
        long mark = ((CommentLive.Batch) live.poll("a", "p1", 0, 5).getResult()).latest();
        live.removed("p1", List.of("c1", "c2"));
        CommentLive.Batch b = (CommentLive.Batch) live.poll("a", "p1", mark, 5).getResult();
        assertEquals(List.of("c1", "c2"), b.removed());
    }

    @Test
    void staleMarkerAsksClientToReload() {
        live.poll("a", "p1", 0, 5);
        CommentLive.Batch b = (CommentLive.Batch) live.poll("a", "p1", 1000, 5).getResult();
        assertTrue(b.reset());
    }

    @Test
    void postsAreIndependent() {
        long mark = ((CommentLive.Batch) live.poll("a", "p2", 0, 5).getResult()).latest();
        live.published("p1", view("c1", "a"));
        assertFalse(live.poll("a", "p2", mark, 5).hasResult());
    }
}
