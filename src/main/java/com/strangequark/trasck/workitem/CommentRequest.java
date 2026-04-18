package com.strangequark.trasck.workitem;

public record CommentRequest(
        String bodyMarkdown,
        Object bodyDocument,
        String visibility
) {
}
