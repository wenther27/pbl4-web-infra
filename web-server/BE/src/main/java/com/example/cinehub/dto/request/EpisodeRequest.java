package com.example.cinehub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EpisodeRequest {
    private String title;

    @NotNull(message = "Số tập không được để trống")
    @Positive(message = "Số tập phải là số dương")
    private Integer episodeNumber;

    @NotBlank(message = "Link video không được để trống")
    private String videoUrl;

    @Positive(message = "Thời lượng phải là số dương")
    private Integer duration;

    private String thumbnailUrl;
}
