package com.example.cinehub.dto.response;

import com.example.cinehub.constant.Movie.MovieStatus;
import com.example.cinehub.constant.Movie.MovieType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
public class MovieDTO {
    private Long id;
    private String title;
    private String description;
    private String posterUrl;
    private String backdropUrl;
    private LocalDate releaseDate;
    private Integer duration;
    private Double rating;
    private Boolean isTrending;
    private Boolean isTopRated;
    private MovieType movieType;
    private MovieStatus status;
    private Set<GenreDTO> genres;

}
