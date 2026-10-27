package com.example.cinehub.controller;

import com.example.cinehub.dto.response.VideoUploadResponse;
import com.example.cinehub.service.VideoStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/uploads")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class VideoUploadController {
    private final VideoStorageService videoStorageService;

    public VideoUploadController(VideoStorageService videoStorageService) {
        this.videoStorageService = videoStorageService;
    }

    @PostMapping("/videos")
    public ResponseEntity<VideoUploadResponse> uploadVideo(@RequestParam("file") MultipartFile file) {
        VideoStorageService.StoredVideo storedVideo = videoStorageService.store(file);
        String videoUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/videos/")
                .path(storedVideo.fileName())
                .toUriString();

        return ResponseEntity.ok(new VideoUploadResponse(
                storedVideo.fileName(),
                videoUrl,
                storedVideo.contentType(),
                storedVideo.size()
        ));
    }
}
