package com.example.cinehub.controller;

import com.example.cinehub.dto.request.WatchHistoryRequest;
import com.example.cinehub.dto.response.WatchHistoryDTO;
import com.example.cinehub.security.CurrentUser;
import com.example.cinehub.service.WatchHistoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
public class WatchHistoryController {
    @Autowired private WatchHistoryService watchHistoryService;
    @Autowired private CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<List<WatchHistoryDTO>> getMyHistory(Authentication authentication) {
        return ResponseEntity.ok(watchHistoryService.getHistoryByUser(currentUser.id(authentication)));
    }

    // Nhan WatchHistoryRequest thay cho Map<String, Object>:
    // co @Valid nen thieu movieId se ra 400 co thong bao, khong con NPE -> 500.
    @PostMapping
    public ResponseEntity<WatchHistoryDTO> saveHistory(@Valid @RequestBody WatchHistoryRequest request,
                                                      Authentication authentication) {
        return ResponseEntity.ok(
                watchHistoryService.saveOrUpdateHistory(currentUser.id(authentication), request));
    }
}
