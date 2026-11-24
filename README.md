# Comments & Replies Service (Java 21)

A thread-safe, in-memory comment and reply service for a discussion platform, built as a LeetCode Project.

Discussions are limited to two levels: **Post → Comment → Reply**.

## API

| Method | Returns |
|---|---|
| `addComment(postId, userId, content)` | `{id, post_id, user_id, content, created_at, replies: []}` |
| `addReply(postId, commentId, userId, content)` | `{id, post_id, comment_id, user_id, content, created_at}` |
| `getComments(postId)` | All comments of the post, each with its replies, in creation order |

## Rules

- `user_id` and `content` must be strings with at least one non-whitespace character. Otherwise the service throws `ValidationError`.
- A missing post, a missing comment, or a comment that belongs to a different post throws `NotFoundError`.
- Input is validated before the post is looked up; for replies the post is checked before the comment.
- Comment IDs and reply IDs are independent, auto-incrementing sequences. A failed call never uses up an ID.
- `created_at` is returned in seconds as a `Double`.

## Design

- **Data model:** `postId → List<Comment>` keeps creation order per post, and `commentId → Comment` makes reply lookups O(1) with no full scans.
- **Concurrency:** one monitor covers the post/comment checks, ID allocation and append, so every write commits atomically and IDs follow commit order. Reads take the same lock and see the thread as it stood at one moment.
- **Snapshots:** every response is built from fresh `LinkedHashMap`/`ArrayList` objects, so callers can mutate results without touching internal state.

## Scaling notes

For a multi-instance deployment, move state to a database with `(post_id, id)` indexes. Use database sequences (or a Snowflake-style generator) for IDs, wrap writes in transactions, and serve reads from a consistent snapshot.
