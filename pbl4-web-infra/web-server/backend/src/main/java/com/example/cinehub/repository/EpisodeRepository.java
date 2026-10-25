package com.example.cinehub.repository;

import com.example.cinehub.entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    // Lọc danh sách tập theo ID phim và sắp xếp tăng dần theo số tập
    List<Episode> findByMovieIdOrderByEpisodeNumberAsc(Long movieId);
}
