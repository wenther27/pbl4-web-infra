package com.example.cinehub.controller;

import com.example.cinehub.dto.request.GenreRequest;
import com.example.cinehub.dto.response.GenreDTO;
import com.example.cinehub.service.GenreService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/genres")
public class GenreController {

    @Autowired private GenreService genreService;

    // Public: FE dung de render menu the loai
    @GetMapping
    public ResponseEntity<List<GenreDTO>> getAllGenres() {
        return ResponseEntity.ok(genreService.getAllGenres());
    }

    // Chi ADMIN (xem SecurityConfig)
    @PostMapping
    public ResponseEntity<GenreDTO> createGenre(@Valid @RequestBody GenreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(genreService.createGenre(request));
    }
}
