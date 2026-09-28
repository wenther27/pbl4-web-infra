package com.example.cinehub.controller;

import com.example.cinehub.constant.Movie.MovieStatus;
import com.example.cinehub.constant.Movie.MovieType;
import com.example.cinehub.dto.request.MovieRequest;
import com.example.cinehub.dto.response.MovieDTO;
import com.example.cinehub.dto.response.PageResponse;
import com.example.cinehub.service.MovieService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    // Chi tiem Service, khong tiem Repository o day
    @Autowired private MovieService movieService;

    // Chan sortBy tuy y de khong bi loi 500 khi FE gui ten cot khong ton tai
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "title", "rating", "releaseDate", "duration", "createdAt");

    @GetMapping
    public ResponseEntity<PageResponse<MovieDTO>> getMovies(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) MovieType movieType,
            @RequestParam(required = false) MovieStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        String safeSortBy = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "id";
        int safeSize = Math.min(Math.max(size, 1), 100); // tranh ?size=1000000
        int safePage = Math.max(page, 0);

        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(safeSortBy).descending()
                : Sort.by(safeSortBy).ascending();

        Pageable pageable = PageRequest.of(safePage, safeSize, sort);
        return ResponseEntity.ok(movieService.searchAndFilterMovies(title, movieType, status, pageable));
    }

    @GetMapping("/all")
    public ResponseEntity<List<MovieDTO>> getAllMoviesForAdmin() {
        return ResponseEntity.ok(movieService.getAllMoviesForAdmin());
    }

    @GetMapping("/trending")
    public ResponseEntity<List<MovieDTO>> getTrendingMovies() {
        return ResponseEntity.ok(movieService.getTrendingMovies());
    }

    @GetMapping("/top-rated")
    public ResponseEntity<List<MovieDTO>> getTopRatedMovies() {
        return ResponseEntity.ok(movieService.getTopRatedMovies());
    }

    @GetMapping("/type")
    public ResponseEntity<List<MovieDTO>> getMoviesByMovieType(@RequestParam MovieType movieType) {
        return ResponseEntity.ok(movieService.getMoviesByMovieType(movieType));
    }

    // Loc phim theo the loai (slug lay tu URL)
    @GetMapping("/genre/{slug}")
    public ResponseEntity<List<MovieDTO>> getMoviesByGenre(@PathVariable String slug) {
        return ResponseEntity.ok(movieService.getMoviesByGenre(slug));
    }

    // Trang chi tiet phim cua FE
    @GetMapping("/{id}")
    public ResponseEntity<MovieDTO> getMovieById(@PathVariable Long id) {
        return ResponseEntity.ok(movieService.getMovieById(id));
    }

    @PostMapping
    public ResponseEntity<MovieDTO> createMovie(@Valid @RequestBody MovieRequest movieRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movieService.createMovie(movieRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MovieDTO> updateMovie(@PathVariable Long id,
                                                @Valid @RequestBody MovieRequest movieRequest) {
        return ResponseEntity.ok(movieService.updateMovie(id, movieRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMovie(@PathVariable Long id) {
        movieService.deleteMovie(id);
        return ResponseEntity.noContent().build();
    }
}
