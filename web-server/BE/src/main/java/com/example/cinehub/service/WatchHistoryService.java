package com.example.cinehub.service;

import com.example.cinehub.dto.request.WatchHistoryRequest;
import com.example.cinehub.dto.response.WatchHistoryDTO;

import java.util.List;

public interface WatchHistoryService {
    List<WatchHistoryDTO> getHistoryByUser(Long userId);

    // Luu lai vi tri dang xem (upsert theo user + phim)
    WatchHistoryDTO saveOrUpdateHistory(Long userId, WatchHistoryRequest request);
}
