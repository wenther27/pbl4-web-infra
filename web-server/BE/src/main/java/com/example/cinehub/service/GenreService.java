package com.example.cinehub.service;

import com.example.cinehub.dto.request.GenreRequest;
import com.example.cinehub.dto.response.GenreDTO;

import java.util.List;

public interface GenreService {
    List<GenreDTO> getAllGenres();
    GenreDTO createGenre(GenreRequest request);
}
