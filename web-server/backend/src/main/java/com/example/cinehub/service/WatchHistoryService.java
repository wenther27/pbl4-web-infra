package com.example.cinehub.service;

import com.example.cinehub.dto.response.WatchHistoryDTO;

import java.util.List;

public interface WatchHistoryService {
    List<WatchHistoryDTO> getHistoryByUser(Long userId);

    WatchHistoryDTO saveOrUpdateHistory(Long userId, Long movieId, Long episodeId, Integer lastPosition);
}
