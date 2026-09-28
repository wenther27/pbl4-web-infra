package com.example.cinehub.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewRequest {
    @NotBlank(message = "Nội dung không được để trống")
    private String content;

    @NotNull(message = "Điểm đánh giá không được để trống")
    @Min(value = 1, message = "Đánh giá phải từ 1 đến 10")
    @Max(value = 10, message = "Đánh giá phải từ 1 đến 10")
    private Integer rating;
}
