package com.example.cinehub.service;

import com.example.cinehub.dto.response.GenreDTO;
import com.example.cinehub.entity.Genre;

import java.util.List;

public interface GenreService {
    List<GenreDTO> getAllGenres();
    GenreDTO createGenre(Genre genre); // Nhận vào Entity và trả về DTO;

}
