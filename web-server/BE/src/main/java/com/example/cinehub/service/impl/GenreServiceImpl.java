package com.example.cinehub.service.impl;

import com.example.cinehub.dto.request.GenreRequest;
import com.example.cinehub.dto.response.GenreDTO;
import com.example.cinehub.entity.Genre;
import com.example.cinehub.exception.BadRequestException;
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
    @Autowired private GenreRepository genreRepository;
    @Autowired private ModelMapper modelMapper;

    @Transactional(readOnly = true)
    @Override
    public List<GenreDTO> getAllGenres() {
        return genreRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public GenreDTO createGenre(GenreRequest request) {
        // name va slug la UNIQUE trong DB -> check truoc cho ra loi 400 co y nghia
        if (genreRepository.existsByName(request.getName()))
            throw new BadRequestException("Thể loại \"" + request.getName() + "\" đã tồn tại");
        if (genreRepository.existsBySlug(request.getSlug()))
            throw new BadRequestException("Slug \"" + request.getSlug() + "\" đã tồn tại");

        Genre genre = new Genre();
        genre.setName(request.getName());
        genre.setSlug(request.getSlug());

        return convertToDTO(genreRepository.save(genre));
    }

    private GenreDTO convertToDTO(Genre genre) {
        return modelMapper.map(genre, GenreDTO.class);
    }
}
