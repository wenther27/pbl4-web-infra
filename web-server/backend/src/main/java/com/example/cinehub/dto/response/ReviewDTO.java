package com.example.cinehub.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReviewDTO {
    private Long id;
    private Integer rating;
    private String content;
    private LocalDateTime createAt;

    private Long userId;
    private String username;
}
