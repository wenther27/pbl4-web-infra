package com.example.cinehub.repository;

import com.example.cinehub.constant.Movie.MovieType;
import com.example.cinehub.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {
    // Tu dong co cac ham: findAll, findById, save, delete...

    // EntityGraph: nap luon genres trong 1 cau query, tranh N+1
    // (moi phim mot query rieng de lay the loai).
    @EntityGraph(attributePaths = "genres")
    Optional<Movie> findWithGenresById(Long id);

    Page<Movie> findByMovieType(MovieType type, Pageable pageable);

    List<Movie> findByMovieType(MovieType type);

    // Loc ngay trong DB thay vi findAll() roi filter tren RAM
    List<Movie> findByIsTrendingTrue();

    List<Movie> findByIsTopRatedTrueOrderByRatingDesc();

    @Query("Select m From Movie m Join m.genres g Where g.slug = :genreSlug")
    List<Movie> findMoviesByGenreSlug(@Param("genreSlug") String genreSlug);
}
