package com.riceguard.api.community;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.WriteBatch;
import com.riceguard.api.common.ApiException;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.PageResult;
import com.riceguard.api.common.Paging;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Slug;
import com.riceguard.api.common.Times;
import com.riceguard.api.community.CommunityDtos.*;
import com.riceguard.api.config.AppProperties;
import com.riceguard.api.media.MediaService;
import com.riceguard.api.notification.NotificationService;
import com.riceguard.api.security.AuthUser;
import com.riceguard.api.user.UserDtos.PublicUser;
import com.riceguard.api.user.UserProfile;
import com.riceguard.api.user.UserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CommunityService {
    private static final int MAX_COMMENTS = 300;

    private final Fs fs;
    private final Refs refs;
    private final Paging paging;
    private final Times times;
    private final UserService users;
    private final MediaService media;
    private final NotificationService notifications;
    private final AppProperties props;

    public CommunityService(Fs fs, Refs refs, Paging paging, Times times, UserService users, MediaService media,
                            NotificationService notifications, AppProperties props) {
        this.fs = fs;
        this.refs = refs;
        this.paging = paging;
        this.times = times;
        this.users = users;
        this.media = media;
        this.notifications = notifications;
        this.props = props;
    }

    // ---------------------------------------------------------------- bài viết

    public PostView create(AuthUser u, PostRequest r) {
        users.getOrCreate(u);
        media.assertOwned(u.uid(), r.images());
        long now = times.now();
        UserProfile profile = users.require(u.uid());
        DocumentReference ref = refs.posts().document();
        Post p = new Post(ref.getId(), u.uid(), profile.province(), r.topic(), r.title().trim(), r.content().trim(),
                r.images() == null ? List.of() : r.images(), 0, 0, now, now);
        fs.set(ref, p);
        return views(u.uid(), List.of(p)).get(0);
    }

    public PostView get(String viewerUid, String id) {
        Post p = fs.require(refs.posts().document(id), Post.class, "Không tìm thấy bài viết");
        return views(viewerUid, List.of(p)).get(0);
    }

    public PageResult<PostView> feed(String viewerUid, String topic, String q, String cursor, int limit) {
        Query base = refs.posts();
        if (topic != null && !topic.isBlank()) base = base.whereEqualTo("topic", topic);
        String needle = Slug.fold(q).trim();
        PageResult<Post> page = paging.page(base, cursor, limit, Post.class,
                needle.isEmpty() ? null : p -> Slug.fold(p.title() + " " + p.content()).contains(needle));
        return PageResult.of(views(viewerUid, page.items()), page.nextCursor());
    }

    public PageResult<PostView> mine(String uid, String cursor, int limit) {
        PageResult<Post> page = paging.page(refs.posts().whereEqualTo("authorId", uid), cursor, limit, Post.class);
        return PageResult.of(views(uid, page.items()), page.nextCursor());
    }

    public PostView update(String uid, String id, PostRequest r) {
        Post old = fs.require(refs.posts().document(id), Post.class, "Không tìm thấy bài viết");
        if (!old.authorId().equals(uid)) throw ApiException.forbidden("Bạn chỉ sửa được bài viết của mình");
        media.assertOwned(uid, r.images());
        var images = r.images() == null ? List.<com.riceguard.api.media.ImageRef>of() : r.images();
        Map<String, Object> m = new HashMap<>();
        m.put("topic", r.topic());
        m.put("title", r.title().trim());
        m.put("content", r.content().trim());
        m.put("images", images.stream().map(fs::toMap).toList());
        m.put("updatedAt", times.now());
        Fs.await(refs.posts().document(id).update(m));
        Set<String> keep = images.stream().map(com.riceguard.api.media.ImageRef::publicId).collect(Collectors.toSet());
        if (old.images() != null) old.images().stream().filter(i -> i.publicId() != null && !keep.contains(i.publicId())).forEach(media::delete);
        return get(uid, id);
    }

    public void delete(AuthUser u, String id) {
        Post p = fs.require(refs.posts().document(id), Post.class, "Không tìm thấy bài viết");
        if (!p.authorId().equals(u.uid()) && !u.admin()) throw ApiException.forbidden("Bạn không có quyền xóa bài viết này");
        removePost(p);
    }

    public void removePost(Post p) {
        fs.deleteRecursive(refs.posts().document(p.id()));
        media.deleteAll(p.images());
    }

    // ---------------------------------------------------------------- thích / lưu

    public long like(String uid, String postId) {
        return toggleLike(uid, postId, true);
    }

    public long unlike(String uid, String postId) {
        return toggleLike(uid, postId, false);
    }

    private long toggleLike(String uid, String postId, boolean like) {
        DocumentReference post = refs.posts().document(postId);
        DocumentReference mark = refs.likes(postId).document(uid);
        Long count = Fs.await(fs.db().runTransaction(tx -> {
            DocumentSnapshot ps = tx.get(post).get();
            DocumentSnapshot ms = tx.get(mark).get();
            if (!ps.exists()) throw ApiException.notFound("Không tìm thấy bài viết");
            long current = ps.getLong("likeCount") == null ? 0 : ps.getLong("likeCount");
            if (like && !ms.exists()) {
                tx.set(mark, Map.of("createdAt", times.now()));
                tx.update(post, "likeCount", FieldValue.increment(1));
                current++;
            } else if (!like && ms.exists()) {
                tx.delete(mark);
                tx.update(post, "likeCount", FieldValue.increment(-1));
                current = Math.max(0, current - 1);
            }
            return current;
        }));
        return count;
    }

    public void save(String uid, String postId) {
        if (!fs.exists(refs.posts().document(postId))) throw ApiException.notFound("Không tìm thấy bài viết");
        Fs.await(refs.savedPosts(uid).document(postId).set(Map.of("createdAt", times.now())));
    }

    public void unsave(String uid, String postId) {
        Fs.await(refs.savedPosts(uid).document(postId).delete());
    }

    public PageResult<PostView> saved(String uid, String cursor, int limit) {
        PageResult<SavedRef> page = paging.page(refs.savedPosts(uid), cursor, limit, SavedRef.class);
        List<DocumentReference> pr = page.items().stream().map(s -> refs.posts().document(s.id())).toList();
        List<Post> posts = new ArrayList<>();
        if (!pr.isEmpty()) {
            for (DocumentSnapshot d : Fs.await(fs.db().getAll(pr.toArray(new DocumentReference[0])))) {
                if (d.exists()) posts.add(fs.read(d, Post.class));
            }
        }
        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < page.items().size(); i++) order.put(page.items().get(i).id(), i);
        posts.sort(Comparator.comparingInt(p -> order.getOrDefault(p.id(), 0)));
        return PageResult.of(views(uid, posts), page.nextCursor());
    }

    public record SavedRef(String id, long createdAt) {}

    // ---------------------------------------------------------------- bình luận

    public CommentView comment(AuthUser u, String postId, CommentRequest r) {
        users.getOrCreate(u);
        Post post = fs.require(refs.posts().document(postId), Post.class, "Không tìm thấy bài viết");
        Comment parent = null;
        if (r.parentId() != null && !r.parentId().isBlank()) {
            parent = fs.require(refs.comments(postId).document(r.parentId()), Comment.class, "Không tìm thấy bình luận để trả lời");
            if (parent.parentId() != null) { // chỉ 1 cấp trả lời
                parent = fs.require(refs.comments(postId).document(parent.parentId()), Comment.class, "Không tìm thấy bình luận để trả lời");
            }
        }
        DocumentReference ref = refs.comments(postId).document();
        Comment c = new Comment(ref.getId(), postId, parent == null ? null : parent.id(), u.uid(), r.text().trim(), 0, List.of(), times.now());
        WriteBatch batch = fs.db().batch();
        batch.set(ref, fs.toMap(c));
        batch.update(refs.posts().document(postId), "commentCount", FieldValue.increment(1));
        Fs.await(batch.commit());

        PublicUser me = users.publicOf(u.uid());
        if (!post.authorId().equals(u.uid())) {
            notifications.create(post.authorId(), "COMMUNITY", "Bình luận mới",
                    me.name() + " đã bình luận bài viết \"" + shorten(post.title()) + "\"", "post", postId);
        }
        if (parent != null && !parent.authorId().equals(u.uid()) && !parent.authorId().equals(post.authorId())) {
            notifications.create(parent.authorId(), "COMMUNITY", "Có người trả lời bạn",
                    me.name() + " đã trả lời bình luận của bạn", "post", postId);
        }
        return commentView(c, post, u.uid(), Map.of(u.uid(), me), List.of());
    }

    /** sort: newest | helpful. Trả cây bình luận 2 cấp. */
    public List<CommentView> comments(String viewerUid, String postId, String sort) {
        Post post = fs.require(refs.posts().document(postId), Post.class, "Không tìm thấy bài viết");
        List<Comment> all = fs.query(refs.comments(postId).orderBy("createdAt", Query.Direction.ASCENDING).limit(MAX_COMMENTS), Comment.class);
        Set<String> ids = all.stream().map(Comment::authorId).collect(Collectors.toSet());
        Map<String, PublicUser> authors = users.lookup(ids);

        Map<String, List<CommentView>> replies = new HashMap<>();
        List<CommentView> top = new ArrayList<>();
        for (Comment c : all) {
            if (c.parentId() != null) {
                replies.computeIfAbsent(c.parentId(), k -> new ArrayList<>()).add(commentView(c, post, viewerUid, authors, List.of()));
            }
        }
        for (Comment c : all) {
            if (c.parentId() == null) top.add(commentView(c, post, viewerUid, authors, replies.getOrDefault(c.id(), List.of())));
        }
        Comparator<CommentView> cmp = "helpful".equals(sort)
                ? Comparator.comparingLong(CommentView::likeCount).reversed().thenComparing(Comparator.comparingLong(CommentView::createdAt).reversed())
                : Comparator.comparingLong(CommentView::createdAt).reversed();
        top.sort(cmp);
        return top;
    }

    public void deleteComment(AuthUser u, String postId, String commentId) {
        Comment c = fs.require(refs.comments(postId).document(commentId), Comment.class, "Không tìm thấy bình luận");
        Post post = fs.require(refs.posts().document(postId), Post.class, "Không tìm thấy bài viết");
        if (!c.authorId().equals(u.uid()) && !post.authorId().equals(u.uid()) && !u.admin()) {
            throw ApiException.forbidden("Bạn không có quyền xóa bình luận này");
        }
        List<? extends DocumentSnapshot> replies = Fs.await(refs.comments(postId).whereEqualTo("parentId", commentId).get()).getDocuments();
        WriteBatch batch = fs.db().batch();
        replies.forEach(d -> batch.delete(d.getReference()));
        batch.delete(refs.comments(postId).document(commentId));
        batch.update(refs.posts().document(postId), "commentCount", FieldValue.increment(-(1L + replies.size())));
        Fs.await(batch.commit());
    }

    public void likeComment(String uid, String postId, String commentId, boolean like) {
        DocumentReference ref = refs.comments(postId).document(commentId);
        Fs.await(fs.db().runTransaction(tx -> {
            DocumentSnapshot s = tx.get(ref).get();
            if (!s.exists()) throw ApiException.notFound("Không tìm thấy bình luận");
            @SuppressWarnings("unchecked") List<String> likedBy = (List<String>) s.get("likedBy");
            boolean has = likedBy != null && likedBy.contains(uid);
            if (like && !has) tx.update(ref, "likedBy", FieldValue.arrayUnion(uid), "likeCount", FieldValue.increment(1));
            if (!like && has) tx.update(ref, "likedBy", FieldValue.arrayRemove(uid), "likeCount", FieldValue.increment(-1));
            return null;
        }));
    }

    // ---------------------------------------------------------------- báo cáo

    public void report(String uid, String postId, String commentId, ReportRequest r) {
        fs.require(refs.posts().document(postId), Post.class, "Không tìm thấy bài viết");
        String target = commentId == null ? postId : commentId;
        Map<String, Object> data = new HashMap<>();
        data.put("type", commentId == null ? "POST" : "COMMENT");
        data.put("postId", postId);
        data.put("targetId", target);
        data.put("reporterId", uid);
        data.put("reason", r.reason());
        if (r.detail() != null && !r.detail().isBlank()) data.put("detail", r.detail().trim());
        data.put("status", "NEW");
        data.put("createdAt", times.now());
        try {
            Fs.await(refs.reports().document(uid + "_" + target).create(data)); // mỗi người chỉ báo cáo một lần cho mỗi nội dung
        } catch (RuntimeException e) {
            if (!isAlreadyExists(e)) throw e;
        }
    }

    private static boolean isAlreadyExists(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof io.grpc.StatusRuntimeException s && s.getStatus().getCode() == io.grpc.Status.Code.ALREADY_EXISTS) return true;
            if (t instanceof com.google.api.gax.rpc.AlreadyExistsException) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- bản nháp

    public Draft saveDraft(String uid, String id, DraftRequest r) {
        media.assertOwned(uid, r.images());
        long now = times.now();
        DocumentReference ref = id == null ? refs.drafts(uid).document() : refs.drafts(uid).document(id);
        long created = now;
        if (id != null) {
            Draft old = fs.require(ref, Draft.class, "Không tìm thấy bản nháp");
            created = old.createdAt();
        }
        Draft d = new Draft(ref.getId(), blank(r.topic()), blank(r.title()), blank(r.content()),
                r.images() == null ? List.of() : r.images(), created, now);
        fs.set(ref, d);
        return d;
    }

    public PageResult<Draft> drafts(String uid, String cursor, int limit) {
        return paging.page(refs.drafts(uid), cursor, limit, Draft.class);
    }

    public void deleteDraft(String uid, String id) {
        Draft d = fs.require(refs.drafts(uid).document(id), Draft.class, "Không tìm thấy bản nháp");
        Fs.await(refs.drafts(uid).document(id).delete());
        media.deleteAll(d.images());
    }

    public PostView publishDraft(AuthUser u, String id) {
        Draft d = fs.require(refs.drafts(u.uid()).document(id), Draft.class, "Không tìm thấy bản nháp");
        if (d.topic() == null || d.title() == null || d.content() == null) {
            throw ApiException.badRequest("Bản nháp cần đủ chủ đề, tiêu đề và nội dung trước khi đăng");
        }
        PostView v = create(u, new PostRequest(d.topic(), d.title(), d.content(), d.images()));
        Fs.await(refs.drafts(u.uid()).document(id).delete());
        return v;
    }

    // ---------------------------------------------------------------- dựng view

    private List<PostView> views(String viewerUid, List<Post> posts) {
        if (posts.isEmpty()) return List.of();
        Set<String> authorIds = posts.stream().map(Post::authorId).collect(Collectors.toSet());
        Map<String, PublicUser> authors = users.lookup(authorIds);
        // Khách chưa đăng nhập (viewerUid == null) chỉ xem: không có thích/lưu/bài của mình.
        Set<String> liked = viewerUid == null ? Set.of() : existing(posts.stream().map(p -> refs.likes(p.id()).document(viewerUid)).toList());
        Set<String> saved = viewerUid == null ? Set.of() : existing(posts.stream().map(p -> refs.savedPosts(viewerUid).document(p.id())).toList());
        List<PostView> out = new ArrayList<>(posts.size());
        for (Post p : posts) {
            String key = p.id();
            out.add(new PostView(p.id(), authors.get(p.authorId()), p.province(), p.topic(), p.title(), p.content(),
                    p.images() == null ? List.of() : p.images(), p.likeCount(), p.commentCount(),
                    liked.contains("likes/" + key), saved.contains("saved/" + key), viewerUid != null && p.authorId().equals(viewerUid),
                    shareUrl(p.id()), p.createdAt(), p.updatedAt()));
        }
        return out;
    }

    /** Trả về tập khóa "likes/{postId}" hoặc "saved/{postId}" của các document tồn tại. */
    private Set<String> existing(List<DocumentReference> refsToCheck) {
        Set<String> out = new HashSet<>();
        if (refsToCheck.isEmpty()) return out;
        for (DocumentSnapshot s : Fs.await(fs.db().getAll(refsToCheck.toArray(new DocumentReference[0])))) {
            if (!s.exists()) continue;
            String parent = s.getReference().getParent().getId();
            if ("likes".equals(parent)) out.add("likes/" + s.getReference().getParent().getParent().getId());
            else if ("savedPosts".equals(parent)) out.add("saved/" + s.getId());
        }
        return out;
    }

    private CommentView commentView(Comment c, Post post, String viewerUid, Map<String, PublicUser> authors, List<CommentView> replies) {
        PublicUser a = authors.get(c.authorId());
        String badge = c.authorId().equals(post.authorId()) ? "AUTHOR" : (a != null && a.verifiedExpert() ? "EXPERT" : null);
        boolean liked = viewerUid != null && c.likedBy() != null && c.likedBy().contains(viewerUid);
        return new CommentView(c.id(), c.parentId(), a, c.text(), c.likeCount(), liked, viewerUid != null && c.authorId().equals(viewerUid), badge, c.createdAt(), replies);
    }

    private String shareUrl(String postId) {
        String base = props.publicBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/posts/" + postId;
    }

    private static String shorten(String s) {
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
