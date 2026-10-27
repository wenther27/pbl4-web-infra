package com.example.cinehub.controller;

import com.example.cinehub.dto.response.ImageUploadResponse;
import com.example.cinehub.service.ImageStorageService;
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
public class ImageUploadController {
    private final ImageStorageService imageStorageService;

    public ImageUploadController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @PostMapping("/images")
    public ResponseEntity<ImageUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "movies") String category) {
        ImageStorageService.StoredImage storedImage = imageStorageService.store(file, category);
        String imageUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/")
                .path(storedImage.category())
                .path("/")
                .path(storedImage.fileName())
                .toUriString();

        return ResponseEntity.ok(new ImageUploadResponse(
                storedImage.fileName(),
                imageUrl,
                storedImage.contentType(),
                storedImage.size()
        ));
    }
}
