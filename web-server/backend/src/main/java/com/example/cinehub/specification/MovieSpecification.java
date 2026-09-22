package com.example.cinehub.specification;

import com.example.cinehub.constant.Movie.MovieStatus;
import com.example.cinehub.constant.Movie.MovieType;
import com.example.cinehub.entity.Movie;
import org.springframework.data.jpa.domain.Specification;

public class MovieSpecification {

    // 1. Lọc theo từ khóa tựa đề phim (Tìm kiếm gần đúng - LIKE %keyword%)
    public static Specification<Movie> hasTitle(String title) {
        return ((root, query, criteriaBuilder) -> {
            if (title == null || title.trim().isEmpty())
                return null;
            return criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), "%" + title.toLowerCase() + "%");
        });
    }

    // 2. Lọc chính xác theo Loại phim (movie, series, anime)
    public static Specification<Movie> hasMovieType(MovieType movieType) {
        return ((root, query, criteriaBuilder) -> {
            if (movieType == null)
                return null;
            return criteriaBuilder.equal(root.get("movieType"), movieType);
        });
    }

    // 3. Lọc chính xác theo Trạng thái (released, upcoming, ongoing)
    public static Specification<Movie> hasStatus(MovieStatus status) {
        return ((root, query, criteriaBuilder) -> {
            if (status == null)
                return null;
            return criteriaBuilder.equal(root.get("status"), status);
        });
    }
}
