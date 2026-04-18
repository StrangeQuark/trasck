package com.strangequark.trasck.workitem;

import com.strangequark.trasck.JsonValues;
import com.strangequark.trasck.activity.Comment;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID workItemId,
        UUID authorId,
        String bodyMarkdown,
        Object bodyDocument,
        String visibility,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getWorkItemId(),
                comment.getAuthorId(),
                comment.getBodyMarkdown(),
                JsonValues.toJavaValue(comment.getBodyDocument()),
                comment.getVisibility(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
