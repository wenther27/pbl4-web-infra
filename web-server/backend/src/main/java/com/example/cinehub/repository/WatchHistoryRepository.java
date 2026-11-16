package com.example.cinehub.repository;

import com.example.cinehub.entity.WatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {
    List<WatchHistory> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<WatchHistory> findByUserIdAndMovieId(Long userId, Long movieId);
}
