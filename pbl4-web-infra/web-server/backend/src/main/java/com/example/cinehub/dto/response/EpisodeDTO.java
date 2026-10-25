package com.example.cinehub.dto.response;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EpisodeDTO {
    private Long id;
    private String title;
    private Integer episodeNumber;
    private String videoUrl;
    private String thumbnailUrl;
    private Integer duration;
}
