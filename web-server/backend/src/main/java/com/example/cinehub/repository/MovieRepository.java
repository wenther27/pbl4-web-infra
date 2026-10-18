package com.example.cinehub.repository;

import com.example.cinehub.constant.Movie.MovieType;
import com.example.cinehub.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {
    // Tự động có các hàm: findAll, findById, save, delete...

    @Override
    Page<Movie> findAll(Pageable pageable);

    Page<Movie> findByMovieType(MovieType type, Pageable pageable);
    // Bạn có thể viết thêm hàm tìm phim theo loại (movie/anime)
    List <Movie> findByMovieType(MovieType type);

    @Query("Select m From Movie m Join m.genres g Where g.slug = :genreSlug")
    List<Movie> findMoviesByGenreSlug(@Param("genreSlug")String genreSlug);

}