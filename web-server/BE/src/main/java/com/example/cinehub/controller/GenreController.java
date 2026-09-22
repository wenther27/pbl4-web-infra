package com.example.cinehub.controller;

import com.example.cinehub.dto.response.GenreDTO;
import com.example.cinehub.entity.Genre;
import com.example.cinehub.service.GenreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/genres")
@CrossOrigin(origins = "http://localhost:5173")
public class GenreController {

    @Autowired
    private GenreService genreService;

    //1. Lấy danh sách tất cả thể loại
    @GetMapping
    public ResponseEntity<List<GenreDTO>> getAllGenres() {

        return ResponseEntity.ok(genreService.getAllGenres());
    }

    //2. Thêm một thể loai mới
    @PostMapping
    public ResponseEntity<GenreDTO> createGenre(@RequestBody Genre genre) {

        return ResponseEntity.ok(genreService.createGenre(genre));
    }
}
