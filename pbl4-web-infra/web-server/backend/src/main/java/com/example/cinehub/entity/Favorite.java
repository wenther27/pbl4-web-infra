package com.example.cinehub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "favorites")
@Getter
@Setter
public class Favorite {
    @EmbeddedId FavoriteId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId") //Khớp với tên fiel trong FavoriteId
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @ManyToOne(fetch =  FetchType.LAZY)
    @MapsId("movieId")
    @JoinColumn(
            name = "movie_id",
            nullable = false
    )
    private Movie movie;

    @Column(name = "added_at")
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }


}
