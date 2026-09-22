package com.example.cinehub.service.impl;

import com.example.cinehub.constant.Movie.MovieStatus;
import com.example.cinehub.constant.Movie.MovieType;
import com.example.cinehub.dto.request.MovieRequest;
import com.example.cinehub.dto.response.GenreDTO;
import com.example.cinehub.dto.response.MovieDTO;
import com.example.cinehub.dto.response.PageResponse;
import com.example.cinehub.entity.Genre;
import com.example.cinehub.entity.Movie;
import com.example.cinehub.exception.ResourceNotFoundException;
import com.example.cinehub.repository.GenreRepository;
import com.example.cinehub.repository.MovieRepository;
import com.example.cinehub.service.MovieService;
import com.example.cinehub.specification.MovieSpecification;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MovieServiceImpl implements MovieService {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired private GenreRepository genreRepository;
    @Autowired private ModelMapper modelMapper;

    @Transactional(readOnly = true)
    @Override
    public List<MovieDTO> getAllMoviesForAdmin() {
        List<Movie> movies = movieRepository.findAll();
        return movies.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovieDTO> getTrendingMovies() {
        List<Movie> movies = movieRepository.findAll();
        return movies.stream()
                .filter(movie -> Boolean.TRUE.equals(movie.getIsTrending()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovieDTO> getMoviesByMovieType(MovieType movieType) {
        List<Movie> movies = movieRepository.findByMovieType(movieType);
        return movies.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovieDTO> getMoviesByGenre(String genreSlug) {
        List<Movie> movies = movieRepository.findMoviesByGenreSlug(genreSlug);

        return movies.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public MovieDTO createMovie(MovieRequest request) {
        Movie movie  = new Movie();
        movie.setTitle(request.getTitle());
        movie.setDescription(request.getDescription());
        movie.setPosterUrl(request.getPosterUrl());
        movie.setBackdropUrl(request.getBackdropUrl());
        movie.setReleaseDate(request.getReleaseDate());
        movie.setDuration(request.getDuration());
        movie.setRating(request.getRating());
        movie.setIsTrending(request.getIsTrending());
        movie.setIsTopRated(request.getIsTopRated());
        movie.setMovieType(request.getMovieType());
        movie.setStatus(request.getStatus());

        if (request.getGenreIds() != null && !request.getGenreIds().isEmpty()) {
            Set<Genre> genres = request.getGenreIds().stream()
                    .map(genredId-> genreRepository.findById(genredId)
                                    .orElseThrow(()-> new ResourceNotFoundException("Không tìm thấy thể loại")))
                    .collect(Collectors.toSet());
            movie.setGenres(genres);
        }
        return convertToDTO(movieRepository.save(movie));
    }

    @Transactional
    @Override
    public MovieDTO updateMovie(Long id, MovieRequest movieDetails) {
        Movie movie = movieRepository.findById(id).orElseThrow(()-> new ResourceNotFoundException("Không tìm thấy phim"));
        movie.setTitle(movieDetails.getTitle());
        movie.setDescription(movieDetails.getDescription());
        movie.setPosterUrl(movieDetails.getPosterUrl());
        movie.setBackdropUrl(movieDetails.getBackdropUrl());
        movie.setReleaseDate(movieDetails.getReleaseDate());
        movie.setDuration(movieDetails.getDuration());
        movie.setRating(movieDetails.getRating());
        movie.setIsTrending(movieDetails.getIsTrending());
        movie.setIsTopRated(movieDetails.getIsTopRated());
        movie.setMovieType(movieDetails.getMovieType());
        movie.setStatus(movieDetails.getStatus());

        if (movieDetails.getGenreIds() != null) {
            Set<Genre> updatedGenres = movieDetails.getGenreIds().stream()
                    .map(genreId -> genreRepository.findById(genreId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thể loại với ID: " + genreId)))
                    .collect(Collectors.toSet());
            movie.setGenres(updatedGenres); // Đè danh sách thể loại mới lên phim
        }
        return convertToDTO(movieRepository.save(movie));
    }

    @Transactional
    @Override
    public void deleteMovie(Long id) {
        Movie m = movieRepository.findById(id).orElseThrow(()-> new ResourceNotFoundException("Không tìm thấy phim"));
        movieRepository.delete(m);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<MovieDTO> getAllMoviesPaged(Pageable pageable) {
        Page<Movie> moviePage = movieRepository.findAll(pageable);

        List<MovieDTO> dtos = moviePage.getContent()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return new PageResponse<>(
                dtos,
                moviePage.getNumber(),
                moviePage.getTotalPages(),
                moviePage.getTotalElements(),
                moviePage.isLast()
        );
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<MovieDTO> searchAndFilterMovies(String title, MovieType movieType, MovieStatus movieStatus, Pageable pageable) {
        Specification<Movie> spec = MovieSpecification.hasTitle(title)
                .and(MovieSpecification.hasMovieType(movieType))
                .and(MovieSpecification.hasStatus(movieStatus));

        Page<Movie> moviePage = movieRepository.findAll(spec, pageable);

        List<MovieDTO> dtos = moviePage.getContent()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return new PageResponse<>(
                dtos,
                moviePage.getNumber(),
                moviePage.getTotalPages(),
                moviePage.getTotalElements(),
                moviePage.isLast()
        );
    }

    //Ham bổ trợ đóng vai trò convert thủ công(Mapping) từ Entity sang DTO sạch sẽ
    private MovieDTO convertToDTO(Movie movie) {
         MovieDTO dto = modelMapper.map(movie, MovieDTO.class);

        // Convert tập hợp các Genres lồng bên trong sang GenreDTO công thức song song
        if (movie.getGenres() != null) {
            Set<GenreDTO> genreDTOs = movie.getGenres().stream().map(
                    genre -> modelMapper.map(genre, GenreDTO.class)
            ).collect(Collectors.toSet());
            dto.setGenres(genreDTOs);
        }
        return dto;
    }



}
