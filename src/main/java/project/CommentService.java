package project;

import java.math.BigInteger;
import java.util.*;

/**
 * In-memory comment and reply service (Post -> Comment -> Reply).
 *
 * <p>All state lives behind one monitor, so ID allocation, writes and thread reads are atomic.
 * Callers always receive freshly built maps and lists, so mutating a response can never change
 * the service's internal state.
 */
public class CommentService {
    @FunctionalInterface
    public interface PostStore {
        boolean exists(BigInteger postId);
    }

    public static class ValidationError extends RuntimeException {
        public ValidationError(String message) {
            super(message);
        }
    }

    public static class NotFoundError extends RuntimeException {
        public NotFoundError(String message) {
            super(message);
        }
    }

    private record Reply(BigInteger id, BigInteger postId, BigInteger commentId,
                         String userId, String content, double createdAt) {}

    private static final class Comment {
        final BigInteger id;
        final BigInteger postId;
        final String userId;
        final String content;
        final double createdAt;
        final List<Reply> replies = new ArrayList<>();

        Comment(BigInteger id, BigInteger postId, String userId, String content, double createdAt) {
            this.id = id;
            this.postId = postId;
            this.userId = userId;
            this.content = content;
            this.createdAt = createdAt;
        }
    }

    private final PostStore postStore;
    private final Object lock = new Object();
    /** postId -> comments of that post, in creation order. */
    private final Map<BigInteger, List<Comment>> commentsByPost = new HashMap<>();
    /** commentId -> comment, for O(1) reply lookups. */
    private final Map<BigInteger, Comment> commentsById = new HashMap<>();
    private BigInteger nextCommentId = BigInteger.ONE;
    private BigInteger nextReplyId = BigInteger.ONE;

    public CommentService(PostStore postStore) {
        this.postStore = Objects.requireNonNull(postStore, "postStore");
    }

    public Map<String, Object> addComment(BigInteger postId, Object userId, Object content) {
        String user = requireText(userId, "user_id");
        String text = requireText(content, "content");
        synchronized (lock) {
            requirePost(postId);
            Comment comment = new Comment(nextCommentId, postId, user, text, now());
            nextCommentId = nextCommentId.add(BigInteger.ONE);
            commentsByPost.computeIfAbsent(postId, k -> new ArrayList<>()).add(comment);
            commentsById.put(comment.id, comment);
            return toMap(comment);
        }
    }

    public Map<String, Object> addReply(
            BigInteger postId, BigInteger commentId, Object userId, Object content) {
        String user = requireText(userId, "user_id");
        String text = requireText(content, "content");
        synchronized (lock) {
            requirePost(postId);
            Comment comment = commentId == null ? null : commentsById.get(commentId);
            if (comment == null || !comment.postId.equals(postId)) {
                throw new NotFoundError("comment not found");
            }
            Reply reply = new Reply(nextReplyId, postId, comment.id, user, text, now());
            nextReplyId = nextReplyId.add(BigInteger.ONE);
            comment.replies.add(reply);
            return toMap(reply);
        }
    }

    public List<Map<String, Object>> getComments(BigInteger postId) {
        synchronized (lock) {
            requirePost(postId);
            List<Comment> comments = commentsByPost.getOrDefault(postId, List.of());
            List<Map<String, Object>> result = new ArrayList<>(comments.size());
            for (Comment comment : comments) {
                result.add(toMap(comment));
            }
            return result;
        }
    }

    private void requirePost(BigInteger postId) {
        if (postId == null || !postStore.exists(postId)) {
            throw new NotFoundError("post not found");
        }
    }

    private static String requireText(Object value, String field) {
        if (!(value instanceof String s) || UnicodeStrings.strip(s).isEmpty()) {
            throw new ValidationError(field + " must be a non-empty string");
        }
        return s;
    }

    private static double now() {
        return System.currentTimeMillis() / 1000.0;
    }

    private static Map<String, Object> toMap(Comment c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id);
        m.put("post_id", c.postId);
        m.put("user_id", c.userId);
        m.put("content", c.content);
        m.put("created_at", c.createdAt);
        List<Map<String, Object>> replies = new ArrayList<>(c.replies.size());
        for (Reply r : c.replies) {
            replies.add(toMap(r));
        }
        m.put("replies", replies);
        return m;
    }

    private static Map<String, Object> toMap(Reply r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.put("post_id", r.postId());
        m.put("comment_id", r.commentId());
        m.put("user_id", r.userId());
        m.put("content", r.content());
        m.put("created_at", r.createdAt());
        return m;
    }
}
