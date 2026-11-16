package com.example.cinehub.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class WatchHistoryDTO {
    private Long id;
    private Long movieId;
    private String movieTitle;
    private String posterUrl;

    private Long episodeId;
    private Integer episodeNumber;

    private Integer lastPosition;
    private LocalDateTime updatedAt;
}
