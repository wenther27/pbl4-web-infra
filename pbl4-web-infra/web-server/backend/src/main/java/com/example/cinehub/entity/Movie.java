package com.example.cinehub.entity;

import com.example.cinehub.constant.Movie.MovieStatus;
import com.example.cinehub.constant.Movie.MovieType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;

@Entity
@Table(name = "movies")
@Getter
@Setter
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;

    @Column(name = "poster_url")
    private String posterUrl;

    @Column(name = "backdrop_url")
    private String backdropUrl;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    private Integer duration;

    private Double rating;

    @Column(name = "is_trending")
    private Boolean isTrending;

    @Column(name = "is_top_rated")
    private Boolean isTopRated;

    @Enumerated(EnumType.STRING)
    @Column(name = "movie_type")
    private MovieType movieType; // Bạn cần tạo thêm Enum MovieType (movie, series, anime)

    @Enumerated(EnumType.STRING)
    private MovieStatus status;

    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @ManyToMany
    @JoinTable(
            name = "movie_genres",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<Genre> genres;


}
