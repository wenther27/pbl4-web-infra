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

    @Autowired private MovieRepository movieRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private ModelMapper modelMapper;

    @Transactional(readOnly = true)
    @Override
    public List<MovieDTO> getAllMoviesForAdmin() {
        return movieRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public MovieDTO getMovieById(Long id) {
        Movie movie = movieRepository.findWithGenresById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim với ID = " + id));
        return convertToDTO(movie);
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovieDTO> getTrendingMovies() {
        return movieRepository.findByIsTrendingTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovieDTO> getTopRatedMovies() {
        return movieRepository.findByIsTopRatedTrueOrderByRatingDesc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovieDTO> getMoviesByMovieType(MovieType movieType) {
        return movieRepository.findByMovieType(movieType).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovieDTO> getMoviesByGenre(String genreSlug) {
        return movieRepository.findMoviesByGenreSlug(genreSlug).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public MovieDTO createMovie(MovieRequest request) {
        Movie movie = new Movie();
        applyRequest(movie, request);
        return convertToDTO(movieRepository.save(movie));
    }

    @Transactional
    @Override
    public MovieDTO updateMovie(Long id, MovieRequest request) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim với ID = " + id));
        applyRequest(movie, request);
        return convertToDTO(movieRepository.save(movie));
    }

    @Transactional
    @Override
    public void deleteMovie(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim với ID = " + id));
        movieRepository.delete(movie);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<MovieDTO> getAllMoviesPaged(Pageable pageable) {
        return toPageResponse(movieRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<MovieDTO> searchAndFilterMovies(String title, MovieType movieType,
                                                       MovieStatus movieStatus, Pageable pageable) {
        Specification<Movie> spec = MovieSpecification.hasTitle(title)
                .and(MovieSpecification.hasMovieType(movieType))
                .and(MovieSpecification.hasStatus(movieStatus));

        return toPageResponse(movieRepository.findAll(spec, pageable));
    }

    // Gan du lieu tu request vao entity. Dung chung cho create va update
    // de 2 cho khong bi lech nhau khi them field moi.
    private void applyRequest(Movie movie, MovieRequest request) {
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

        if (request.getGenreIds() != null) {
            Set<Genre> genres = request.getGenreIds().stream()
                    .map(genreId -> genreRepository.findById(genreId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Không tìm thấy thể loại với ID = " + genreId)))
                    .collect(Collectors.toSet());
            movie.setGenres(genres);
        }
    }

    private PageResponse<MovieDTO> toPageResponse(Page<Movie> moviePage) {
        List<MovieDTO> dtos = moviePage.getContent().stream()
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

    // Convert Entity -> DTO. Phai map genres thu cong vi ModelMapper dang chay
    // MatchingStrategies.STRICT nen khong tu dong xu ly collection long nhau.
    private MovieDTO convertToDTO(Movie movie) {
        MovieDTO dto = modelMapper.map(movie, MovieDTO.class);

        if (movie.getGenres() != null) {
            Set<GenreDTO> genreDTOs = movie.getGenres().stream()
                    .map(genre -> modelMapper.map(genre, GenreDTO.class))
                    .collect(Collectors.toSet());
            dto.setGenres(genreDTOs);
        }
        return dto;
    }
}
