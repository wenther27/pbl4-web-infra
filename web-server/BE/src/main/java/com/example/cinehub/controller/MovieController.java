package com.example.cinehub.controller;

import com.example.cinehub.constant.Movie.MovieStatus;
import com.example.cinehub.constant.Movie.MovieType;
import com.example.cinehub.dto.request.MovieRequest;
import com.example.cinehub.dto.response.MovieDTO;
import com.example.cinehub.dto.response.PageResponse;
import com.example.cinehub.entity.Movie;
import com.example.cinehub.service.MovieService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
@CrossOrigin(origins = "http://localhost:5173") // Cấp quyền cho Vite gọi API
public class MovieController {

    @Autowired
    private MovieService movieService; // Chỉ tiêm Service, tuyệt đối không tiêm Repository ở đây nữa!

    @GetMapping
    public ResponseEntity<PageResponse<MovieDTO>> getMovies(@RequestParam(required = false) String title,
                                                            @RequestParam(required = false) MovieType movieType,
                                                            @RequestParam(required = false) MovieStatus status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "10") int size,
                                                            @RequestParam(defaultValue = "id") String sortBy,       //Mặc định xắp sếp theo ID
                                                            @RequestParam(defaultValue = "asc") String direction ) {// Mặc định tăng dần
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending()
                                                                    : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(movieService.searchAndFilterMovies(title, movieType, status, pageable));
    }

    @GetMapping("/all")
    public ResponseEntity<List<MovieDTO>> getAllMoviesForAdmin() {
        return ResponseEntity.ok(movieService.getAllMoviesForAdmin());
    }

    @GetMapping("/trending")
    public List<MovieDTO> getTrendingMovies() {
        return movieService.getTrendingMovies();
    }

    @GetMapping("/type")
    public ResponseEntity<List<MovieDTO>> getMoviesByMovieType(@RequestParam MovieType movieType) {
        return ResponseEntity.ok(movieService.getMoviesByMovieType(movieType));
    }
    // 🌟 API Lọc phim theo thể loại (Dùng PathVariable để lấy slug từ URL)
    @GetMapping("/genre/{slug}")
    public ResponseEntity<List<MovieDTO>> getMoviesByGenre(@PathVariable String slug) {
        List<MovieDTO> movies = movieService.getMoviesByGenre(slug);
        return ResponseEntity.ok(movies);
    }

    @PostMapping
    public ResponseEntity<MovieDTO> createMovie(@Valid @RequestBody MovieRequest movieRequest) {
        return ResponseEntity.ok(movieService.createMovie(movieRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MovieDTO> updateMovie(@PathVariable Long id,@Valid @RequestBody MovieRequest movie) {
        return ResponseEntity.ok(movieService.updateMovie(id,movie));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteMovie(@PathVariable Long id) {
        movieService.deleteMovie(id);
        return ResponseEntity.ok("Đã xóa phim thành công");
    }
}