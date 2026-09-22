package com.example.cinehub.service;


import com.example.cinehub.dto.response.FavoriteDTO;

import java.util.List;

public interface FavoriteService {
    List<FavoriteDTO> getFavoritesByUser(Long userId);
    FavoriteDTO addFavorite(Long userId, Long movieId);
    void deleteFavorite(Long userId, Long movieId);
    //Kiểm tra phim có trong yêu thích không
    boolean isFavorited(Long userId, Long movieId);
}
