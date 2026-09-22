package com.example.cinehub.service;

import com.example.cinehub.dto.response.ReviewDTO;
import com.example.cinehub.entity.Review;

import java.util.List;

public interface ReviewService {
    List<ReviewDTO> getReviewsByMovieId(Long movieId);
    ReviewDTO addReview(Long movieId,Long userId ,Review review);
    void deleteReview(Long reviewId);
}
