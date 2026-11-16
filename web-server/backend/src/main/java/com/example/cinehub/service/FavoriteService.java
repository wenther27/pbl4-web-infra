package com.example.cinehub.service;

import com.example.cinehub.dto.response.FavoriteDTO;

import java.util.List;

public interface FavoriteService {
    List<FavoriteDTO> getFavoritesByUser(Long userId);
    FavoriteDTO addFavorite(Long userId, Long movieId);
    void deleteFavorite(Long userId, Long movieId);
    boolean isFavorited(Long userId, Long movieId);
}
