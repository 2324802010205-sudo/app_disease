package com.riceguard.api.community;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.riceguard.api.community.CommunityDtos.CommentView;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Bình luận thời gian thực mà không tốn thêm lượt đọc Firestore.
 * <p>
 * Mọi bình luận đều đi qua backend nên backend giữ trong bộ nhớ vài sự kiện gần nhất của từng bài (thêm/xóa).
 * App mở bài viết sẽ giữ một yêu cầu chờ (long-poll): có sự kiện mới thì trả ngay cho tất cả người đang xem,
 * không có thì đóng sau {@code wait} giây và app hỏi lại. Chi phí Firestore: 1 lần ghi cho mỗi bình luận (như bình thường),
 * 0 lần đọc cho việc "chờ" và "đẩy" tới N người xem, bất kể N.
 * <p>
 * Giới hạn: bộ đệm nằm trong một tiến trình. Chạy nhiều bản backend thì cần đưa bộ đệm này ra ngoài (Redis pub/sub)
 * hoặc để mỗi bản nghe một tài liệu Firestore nhỏ; hiện chạy một bản nên chưa cần.
 */
@Component
public class CommentLive {
    /** reset=true: app lỡ sự kiện (backend khởi động lại / bộ đệm đã bị dọn), cần tải lại toàn bộ bình luận một lần. */
    public record Batch(long latest, boolean reset, List<CommentView> comments, List<String> removed) {}

    private record Event(long seq, CommentView added, String removedId) {}

    private record Waiter(String viewerUid, long after, DeferredResult<Batch> result) {}

    private static final int MAX_EVENTS = 100;

    private static final class Channel {
        final long startedAt = System.currentTimeMillis();
        final ArrayDeque<Event> events = new ArrayDeque<>();
        final List<Waiter> waiters = new ArrayList<>();
        long last = startedAt;
    }

    private final Cache<String, Channel> channels = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(15)).maximumSize(2000).build();

    public void published(String postId, CommentView view) {
        push(postId, new Event(0, view, null));
    }

    public void removed(String postId, List<String> commentIds) {
        for (String id : commentIds) push(postId, new Event(0, null, id));
    }

    private void push(String postId, Event proto) {
        Channel ch = channels.get(postId, k -> new Channel());
        synchronized (ch) {
            long seq = Math.max(System.currentTimeMillis(), ch.last + 1);
            ch.last = seq;
            ch.events.addLast(new Event(seq, proto.added(), proto.removedId()));
            while (ch.events.size() > MAX_EVENTS) ch.events.removeFirst();
            List<Waiter> ready = new ArrayList<>(ch.waiters);
            ch.waiters.clear();
            for (Waiter w : ready) w.result().setResult(collect(ch, w.viewerUid(), w.after()));
        }
    }

    /**
     * after=0: lần đồng bộ đầu, trả ngay mốc hiện tại. after>0: trả sự kiện mới hơn mốc đó, hoặc chờ tối đa waitSeconds.
     * latest chỉ tăng khi có sự kiện (không tăng khi hết giờ) để không bỏ sót bình luận ghi cùng lúc.
     */
    public DeferredResult<Batch> poll(String viewerUid, String postId, long after, int waitSeconds) {
        long timeoutMs = Math.max(1, Math.min(waitSeconds, 25)) * 1000L;
        DeferredResult<Batch> result = new DeferredResult<>(timeoutMs, new Batch(after, false, List.of(), List.of()));
        Channel ch = channels.get(postId, k -> new Channel());
        synchronized (ch) {
            if (after == 0) {
                // Dời ch.last tới mốc vừa cấp: bình luận đến ngay trong cùng mili-giây vẫn có seq lớn hơn mốc.
                ch.last = Math.max(System.currentTimeMillis(), ch.last);
                result.setResult(new Batch(ch.last, false, List.of(), List.of()));
            } else if (after < ch.startedAt) {
                result.setResult(new Batch(System.currentTimeMillis(), true, List.of(), List.of()));
            } else {
                Batch now = collect(ch, viewerUid, after);
                if (!now.comments().isEmpty() || !now.removed().isEmpty()) {
                    result.setResult(now);
                } else {
                    Waiter w = new Waiter(viewerUid, after, result);
                    ch.waiters.add(w);
                    Runnable drop = () -> { synchronized (ch) { ch.waiters.remove(w); } };
                    result.onTimeout(() -> { drop.run(); });
                    result.onCompletion(drop);
                }
            }
        }
        return result;
    }

    private static Batch collect(Channel ch, String viewerUid, long after) {
        List<CommentView> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        long latest = after;
        for (Event e : ch.events) {
            if (e.seq() <= after) continue;
            latest = Math.max(latest, e.seq());
            if (e.added() != null) added.add(forViewer(e.added(), viewerUid));
            else removed.add(e.removedId());
        }
        return new Batch(latest, false, added, removed);
    }

    private static CommentView forViewer(CommentView v, String viewerUid) {
        boolean mine = viewerUid != null && v.author() != null && viewerUid.equals(v.author().id());
        return new CommentView(v.id(), v.parentId(), v.author(), v.text(), v.likeCount(), false, mine, v.badge(), v.createdAt(), List.of());
    }
}
