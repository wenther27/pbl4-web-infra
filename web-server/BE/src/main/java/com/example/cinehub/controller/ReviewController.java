package com.example.cinehub.controller;

import com.example.cinehub.dto.request.ReviewRequest;
import com.example.cinehub.dto.response.ReviewDTO;
import com.example.cinehub.security.CurrentUser;
import com.example.cinehub.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReviewController {
    @Autowired private ReviewService reviewService;
    @Autowired private CurrentUser currentUser;

    // Public: ai cung doc duoc binh luan
    @GetMapping("/movies/{movieId}/reviews")
    public ResponseEntity<List<ReviewDTO>> getReviewsByMovieId(@PathVariable Long movieId) {
        return ResponseEntity.ok(reviewService.getReviewsByMovieId(movieId));
    }

    @PostMapping("/movies/{movieId}/reviews")
    public ResponseEntity<ReviewDTO> addReview(@PathVariable Long movieId,
                                              Authentication authentication,
                                              @Valid @RequestBody ReviewRequest request) {
        Long userId = currentUser.id(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.addReview(movieId, userId, request));
    }

    // Chi chu binh luan hoac ADMIN moi xoa duoc - kiem tra trong ReviewService
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId,
                                             Authentication authentication) {
        reviewService.deleteReview(reviewId,
                currentUser.id(authentication),
                currentUser.isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }
}
