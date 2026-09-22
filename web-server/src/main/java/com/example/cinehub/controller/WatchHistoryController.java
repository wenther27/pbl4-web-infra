package com.example.cinehub.controller;

import com.example.cinehub.dto.response.WatchHistoryDTO;
import com.example.cinehub.entity.User;
import com.example.cinehub.repository.UserRepository;
import com.example.cinehub.service.WatchHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
@CrossOrigin(origins = "http://localhost:5173")
public class WatchHistoryController {
    @Autowired private WatchHistoryService watchHistoryService;
    @Autowired private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<WatchHistoryDTO>> getMyHistory(Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(watchHistoryService.getHistoryByUser(userId));
    }

    @PostMapping
    public ResponseEntity<WatchHistoryDTO> saveHistory(@RequestBody Map<String, Object> body,
                                                       Authentication authentication) {
        Long userId = getUserId(authentication);
        Long movieId = Long.valueOf(body.get("movieId").toString());
        Integer lastPostion = Integer.valueOf(body.get("lastPosition").toString());

        Long episodeId = body.get("episodeId") != null ? Long.valueOf(body.get("episodeId").toString())
                                                        : null;
        return ResponseEntity.ok(watchHistoryService.saveOrUpdateHistory(userId, movieId, episodeId, lastPostion));
    }

    private Long getUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(()->new RuntimeException("Không tìm thấy user"));
        return user.getId();
    }
}
