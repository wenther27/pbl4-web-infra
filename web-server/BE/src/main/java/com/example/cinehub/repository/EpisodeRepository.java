package com.example.cinehub.repository;

import com.example.cinehub.entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    // Loc danh sach tap theo ID phim va sap xep tang dan theo so tap
    List<Episode> findByMovieIdOrderByEpisodeNumberAsc(Long movieId);

    // Bang episodes co UNIQUE (movie_id, episode_number) -> check truoc de tra 400
    // thay vi de vo constraint thanh loi 500
    boolean existsByMovieIdAndEpisodeNumber(Long movieId, Integer episodeNumber);
}
