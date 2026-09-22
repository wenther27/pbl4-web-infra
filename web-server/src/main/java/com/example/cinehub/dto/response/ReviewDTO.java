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

    // Chỉ lấy tên và ID của User để Front-End hiển thị, không lấy password hay email bảo mật
    private Long userId;
    private String username;
}
