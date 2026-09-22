package com.example.cinehub.controller;

import com.example.cinehub.dto.response.ReviewDTO;
import com.example.cinehub.dto.response.ReviewRequest;
import com.example.cinehub.entity.Review;
import com.example.cinehub.entity.User;
import com.example.cinehub.exception.ResourceNotFoundException;
import com.example.cinehub.repository.UserRepository;
import com.example.cinehub.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class ReviewController {
    @Autowired
    private ReviewService reviewService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/movies/{movieId}/reviews")
    public ResponseEntity<List<ReviewDTO>> getReviewsByMovieId(@PathVariable Long movieId) {
        return ResponseEntity.ok(reviewService.getReviewsByMovieId(movieId));
    }

    @PostMapping("/movies/{movieId}/reviews")
    public ResponseEntity<ReviewDTO> addReview(@PathVariable Long movieId, Authentication authentication, @Valid @RequestBody ReviewRequest reviewRequest) {
        Long userId = getUserId(authentication);
        Review review = new Review();
        review.setContent(reviewRequest.getContent());
        review.setRating(reviewRequest.getRating());
        return ResponseEntity.ok(reviewService.addReview(movieId, userId, review));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<String> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok("Đã xóa bình luận thành công");
    }

    private Long getUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user"));
        return user.getId();
    }
}
