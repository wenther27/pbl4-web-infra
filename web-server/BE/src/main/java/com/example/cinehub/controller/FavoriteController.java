package com.example.cinehub.controller;

import com.example.cinehub.dto.response.FavoriteDTO;
import com.example.cinehub.security.CurrentUser;
import com.example.cinehub.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    @Autowired private FavoriteService favoriteService;
    @Autowired private CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<List<FavoriteDTO>> getMyFavorites(Authentication authentication) {
        return ResponseEntity.ok(favoriteService.getFavoritesByUser(currentUser.id(authentication)));
    }

    @PostMapping("/{movieId}")
    public ResponseEntity<FavoriteDTO> addFavorite(@PathVariable Long movieId,
                                                  Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(favoriteService.addFavorite(currentUser.id(authentication), movieId));
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> deleteFavorite(@PathVariable Long movieId,
                                               Authentication authentication) {
        favoriteService.deleteFavorite(currentUser.id(authentication), movieId);
        return ResponseEntity.noContent().build();
    }

    // FE dung de hien thi icon tim do hay trang
    @GetMapping("/{movieId}/check")
    public ResponseEntity<Boolean> isFavorited(@PathVariable Long movieId,
                                               Authentication authentication) {
        return ResponseEntity.ok(favoriteService.isFavorited(currentUser.id(authentication), movieId));
    }
}
