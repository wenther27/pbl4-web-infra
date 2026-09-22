package com.example.cinehub.repository;

import com.example.cinehub.entity.Favorite;
import com.example.cinehub.entity.FavoriteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

    List<Favorite> findByIdUserId(Long userId);

    boolean existsByIdUserIdAndIdMovieId(Long userId, Long movieId);

    //Xoóa favorite theo userId và movieId
    //Phải dùng @Modifying + @Query vì composite key không hổ trợ derived delete
    @Modifying
    @Transactional
    @Query("Delete From Favorite f Where f.id.userId = :userId And f.id.movieId = :movieId")
    void deleteByUserIdAndMovieId(@Param("userId") Long userId, @Param("movieId") Long movieId);
}
