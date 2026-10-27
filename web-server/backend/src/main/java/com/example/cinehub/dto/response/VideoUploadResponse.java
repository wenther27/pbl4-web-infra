package com.example.cinehub.dto.response;

public record VideoUploadResponse(
        String fileName,
        String url,
        String contentType,
        long size
) {
}
