package com.example.cinehub.dto.response;

public record ImageUploadResponse(
        String fileName,
        String url,
        String contentType,
        long size
) {
}
