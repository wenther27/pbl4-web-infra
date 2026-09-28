package com.example.cinehub.service.impl;

import com.example.cinehub.dto.request.ReviewRequest;
import com.example.cinehub.dto.response.ReviewDTO;
import com.example.cinehub.entity.Movie;
import com.example.cinehub.entity.Review;
import com.example.cinehub.entity.User;
import com.example.cinehub.exception.ResourceNotFoundException;
import com.example.cinehub.repository.MovieRepository;
import com.example.cinehub.repository.ReviewRepository;
import com.example.cinehub.repository.UserRepository;
import com.example.cinehub.service.ReviewService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewServiceImpl implements ReviewService {
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private ModelMapper modelMapper;

    @Transactional(readOnly = true)
    @Override
    public List<ReviewDTO> getReviewsByMovieId(Long movieId) {
        if (!movieRepository.existsById(movieId))
            throw new ResourceNotFoundException("Không tìm thấy phim với ID = " + movieId);

        return reviewRepository.findByMovieIdOrderByCreateAtDesc(movieId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public ReviewDTO addReview(Long movieId, Long userId, ReviewRequest request) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim với ID = " + movieId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với ID = " + userId));

        Review review = new Review();
        review.setContent(request.getContent());
        review.setRating(request.getRating());
        review.setMovie(movie);
        review.setUser(user);

        return convertToDTO(reviewRepository.save(review));
    }

    @Transactional
    @Override
    public void deleteReview(Long reviewId, Long requesterId, boolean isAdmin) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bình luận với ID = " + reviewId));

        // Khong co buoc nay thi bat ky user dang nhap nao cung xoa duoc binh luan cua nguoi khac
        if (!isAdmin && !review.getUser().getId().equals(requesterId))
            throw new AccessDeniedException("Bạn chỉ có thể xóa bình luận của chính mình");

        reviewRepository.delete(review);
    }

    private ReviewDTO convertToDTO(Review review) {
        ReviewDTO dto = modelMapper.map(review, ReviewDTO.class);
        dto.setUserId(review.getUser().getId());
        dto.setUsername(review.getUser().getUsername());
        return dto;
    }
}
