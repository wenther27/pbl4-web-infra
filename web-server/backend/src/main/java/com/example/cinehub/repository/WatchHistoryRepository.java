package com.example.cinehub.repository;

import com.example.cinehub.entity.WatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {
    //Xắp xếp
    List<WatchHistory> findByUserIdOrderByUpdatedAtDesc(Long userId);

    // Tìm 1 record theo userId + movieId (dùng cho upsert)
    Optional<WatchHistory> findByUserIdAndMovieId(Long userId, Long movieId);
}
