package com.example.cinehub.controller;

import com.example.cinehub.dto.response.FavoriteDTO;
import com.example.cinehub.entity.User;
import com.example.cinehub.exception.ResourceNotFoundException;
import com.example.cinehub.repository.UserRepository;
import com.example.cinehub.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@CrossOrigin(origins = "http://localhost:5173")
public class FavoriteController {
    @Autowired private FavoriteService favoriteService;
    @Autowired private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<FavoriteDTO>> getMyFavorites(Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(favoriteService.getFavoritesByUser(userId));
    }

    @PostMapping("/{movieId}")
    public ResponseEntity<FavoriteDTO> addFavorite(@PathVariable Long movieId, Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(favoriteService.addFavorite(userId, movieId));
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<String> deleteFavorite(@PathVariable Long movieId, Authentication authentication) {
        Long userId = getUserId(authentication);
        favoriteService.deleteFavorite(userId, movieId);
        return ResponseEntity.ok("Đã xóa khỏi danh sách yêu thích");
    }

    // FE dùng để hiển thị icon tim đỏ hay trắng
    @GetMapping("{movieId}/check")
    public ResponseEntity<Boolean> isFavorited(@PathVariable Long movieId, Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(favoriteService.isFavorited(userId, movieId));
    }

    private Long getUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy user"));
        return user.getId();
    }
}
