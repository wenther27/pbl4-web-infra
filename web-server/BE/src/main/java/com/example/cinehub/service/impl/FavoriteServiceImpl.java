package com.example.cinehub.service.impl;

import com.example.cinehub.dto.response.FavoriteDTO;
import com.example.cinehub.entity.Favorite;
import com.example.cinehub.entity.FavoriteId;
import com.example.cinehub.entity.Movie;
import com.example.cinehub.entity.User;
import com.example.cinehub.exception.BadRequestException;
import com.example.cinehub.exception.ResourceNotFoundException;
import com.example.cinehub.repository.FavoriteRepository;
import com.example.cinehub.repository.MovieRepository;
import com.example.cinehub.repository.UserRepository;
import com.example.cinehub.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FavoriteServiceImpl implements FavoriteService {
    @Autowired private FavoriteRepository favoriteRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MovieRepository movieRepository;

    @Transactional(readOnly = true)
    @Override
    public List<FavoriteDTO> getFavoritesByUser(Long userId) {
        return favoriteRepository.findByIdUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public FavoriteDTO addFavorite(Long userId, Long movieId) {
        if (favoriteRepository.existsByIdUserIdAndIdMovieId(userId, movieId))
            throw new BadRequestException("Phim đã có trong danh sách yêu thích");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với ID = " + userId));
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim với ID = " + movieId));

        Favorite favorite = new Favorite();
        favorite.setId(new FavoriteId(userId, movieId));
        favorite.setUser(user);
        favorite.setMovie(movie);

        return convertToDTO(favoriteRepository.save(favorite));
    }

    @Transactional
    @Override
    public void deleteFavorite(Long userId, Long movieId) {
        // Khong check thi API luon tra 200 du phim chua he duoc yeu thich
        if (!favoriteRepository.existsByIdUserIdAndIdMovieId(userId, movieId))
            throw new ResourceNotFoundException("Phim không có trong danh sách yêu thích");

        favoriteRepository.deleteByUserIdAndMovieId(userId, movieId);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isFavorited(Long userId, Long movieId) {
        return favoriteRepository.existsByIdUserIdAndIdMovieId(userId, movieId);
    }

    // Map thu cong: FavoriteDTO lay du lieu tu movie long ben trong,
    // ModelMapper (STRICT) khong tu suy ra duoc movie.title -> movieTitle.
    private FavoriteDTO convertToDTO(Favorite favorite) {
        FavoriteDTO dto = new FavoriteDTO();
        dto.setMovieId(favorite.getMovie().getId());
        dto.setMovieTitle(favorite.getMovie().getTitle());
        dto.setPosterUrl(favorite.getMovie().getPosterUrl());
        dto.setAddedAt(favorite.getAddedAt());
        return dto;
    }
}
