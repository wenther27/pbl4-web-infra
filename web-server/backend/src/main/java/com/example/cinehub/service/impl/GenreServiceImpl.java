package com.example.cinehub.service.impl;

import com.example.cinehub.dto.response.GenreDTO;
import com.example.cinehub.entity.Genre;
import com.example.cinehub.repository.GenreRepository;
import com.example.cinehub.service.GenreService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GenreServiceImpl implements GenreService {
    @Autowired
    private GenreRepository genreRepository;
    @Autowired private ModelMapper modelMapper;

    @Transactional(readOnly = true)
    @Override
    public List<GenreDTO> getAllGenres(){
        List<Genre> genres = genreRepository.findAll();
        return genres.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public GenreDTO createGenre(Genre genre) {
        Genre saveGenre = genreRepository.save(genre);
        return convertToDTO(saveGenre);
    }

    // Hàm Convert ừ Genre Entity sang GenreDTO
    private GenreDTO convertToDTO(Genre genre) {
        return modelMapper.map(genre, GenreDTO.class);
    }

}
