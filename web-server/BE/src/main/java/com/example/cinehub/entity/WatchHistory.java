package com.example.cinehub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "watch_history")
@Getter
@Setter
public class WatchHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "last_position")
    private Integer lastPosition = 0;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "movie_id",
            nullable = false
    )
    private Movie movie;

    //Có thẻ null vì phim lẻ có thể không có tập
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id")
    private Episode episode;

    @PrePersist
    @PreUpdate
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }
}
