package com.example.cinehub.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class FavoriteDTO {
    private Long movieId;
    private String movieTitle;
    private String posterUrl;
    private LocalDateTime addedAt;
}
