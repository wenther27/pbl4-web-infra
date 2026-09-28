package com.example.cinehub.service;

import com.example.cinehub.dto.request.ReviewRequest;
import com.example.cinehub.dto.response.ReviewDTO;

import java.util.List;

public interface ReviewService {
    List<ReviewDTO> getReviewsByMovieId(Long movieId);
    ReviewDTO addReview(Long movieId, Long userId, ReviewRequest request);

    // requesterId + isAdmin de kiem tra quyen: chi chu binh luan hoac ADMIN moi xoa duoc
    void deleteReview(Long reviewId, Long requesterId, boolean isAdmin);
}
