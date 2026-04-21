package com.strangequark.trasck.project;

import com.strangequark.trasck.activity.Comment;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicWorkItemCommentResponse(
        UUID id,
        UUID workItemId,
        String bodyMarkdown,
        OffsetDateTime createdAt
) {
    static PublicWorkItemCommentResponse from(Comment comment) {
        return new PublicWorkItemCommentResponse(
                comment.getId(),
                comment.getWorkItemId(),
                comment.getBodyMarkdown(),
                comment.getCreatedAt()
        );
    }
}
