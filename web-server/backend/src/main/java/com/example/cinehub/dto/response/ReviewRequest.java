package com.example.cinehub.dto.response;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewRequest {
    @NotBlank(message = "Nội dung không được để trống")
    private String content;

    @Min(value = 1, message = "Đánh gia phải từ 1 đến 10")
    @Max(value = 10, message = "Đánh gia phải từ 1 đến 10")
    private Integer rating;
}
