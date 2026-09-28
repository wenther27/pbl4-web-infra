package com.example.cinehub.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WatchHistoryRequest {
    @NotNull(message = "movieId không được để trống")
    private Long movieId;

    // Null với phim lẻ, có giá trị với series/anime
    private Long episodeId;

    @NotNull(message = "lastPosition không được để trống")
    @PositiveOrZero(message = "lastPosition phải >= 0")
    private Integer lastPosition;
}
