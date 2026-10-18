package com.example.cinehub.dto.request;

import com.example.cinehub.constant.Movie.MovieStatus;
import com.example.cinehub.constant.Movie.MovieType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
public class MovieRequest {
    @NotBlank(message = "Tên phim không được để trống")
    private String title;

    private String description;
    private String posterUrl;
    private String backdropUrl;
    private LocalDate releaseDate;

    @NotNull(message = "Thời lượng phim không được để trống")
    @Positive(message = "Thời lượng phim phải là số dương")
    private Integer duration;

    @NotNull(message = "Điểm đánh giá không được để trống")
    @Min(value = 0, message = "Điểm đánh giá thấp nhấp là 0")
    @Max(value = 10, message = "Điểm đánh giá cao nhất là 10")
    private Double rating;

    private Boolean isTrending = false;
    private Boolean isTopRated = false;

    @NotNull(message = "Thể loại phim không được để trống")
    private MovieType movieType;

    @NotNull(message = "Trạng thái phim không được để trống")
    private MovieStatus status;

    // Phân tích: Front-End truyền danh sách ID thể loại lên (Ví dụ: [1, 2] cho Hành Động, Viễn Tưởng)
    private Set<Integer> genreIds;
}
