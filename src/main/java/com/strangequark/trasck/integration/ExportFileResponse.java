package com.strangequark.trasck.integration;

public record ExportFileResponse(
        String filename,
        String contentType,
        String checksum,
        byte[] bytes
) {
}
