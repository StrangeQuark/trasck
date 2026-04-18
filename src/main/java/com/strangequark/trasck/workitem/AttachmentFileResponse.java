package com.strangequark.trasck.workitem;

public record AttachmentFileResponse(
        String filename,
        String contentType,
        byte[] bytes
) {
}
