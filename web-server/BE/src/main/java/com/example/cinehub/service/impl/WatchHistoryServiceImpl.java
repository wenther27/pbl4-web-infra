package com.example.cinehub.service.impl;

import com.example.cinehub.dto.response.WatchHistoryDTO;
import com.example.cinehub.entity.Episode;
import com.example.cinehub.entity.Movie;
import com.example.cinehub.entity.User;
import com.example.cinehub.entity.WatchHistory;
import com.example.cinehub.exception.ResourceNotFoundException;
import com.example.cinehub.repository.EpisodeRepository;
import com.example.cinehub.repository.MovieRepository;
import com.example.cinehub.repository.UserRepository;
import com.example.cinehub.repository.WatchHistoryRepository;
import com.example.cinehub.service.WatchHistoryService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WatchHistoryServiceImpl implements WatchHistoryService {
    @Autowired private WatchHistoryRepository watchHistoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private ModelMapper modelMapper;

    @Transactional(readOnly = true)
    @Override
    public List<WatchHistoryDTO> getHistoryByUser(Long userId) {
        List<WatchHistory> watchHistories = watchHistoryRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return watchHistories.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public WatchHistoryDTO saveOrUpdateHistory(Long userId, Long movieId,
                                               Long episodeId, Integer lastPosition) {
        User user = userRepository.findById(userId)
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy user"));
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy phim"));

        // Upsert: tìm record cũ nếu có, không có thì tạo mới
        // Tránh tạo nhiều record trùng nhau cho cùng 1 user + phim
        WatchHistory watchHistory = watchHistoryRepository
                .findByUserIdAndMovieId(userId, movieId)
                .orElse(new WatchHistory());
        watchHistory.setUser(user);
        watchHistory.setMovie(movie);
        watchHistory.setLastPosition(lastPosition);
        watchHistory.setUpdatedAt(LocalDateTime.now());


        if (episodeId != null) {
            Episode episode = episodeRepository.findById(episodeId)
                    .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy tập phim"));
            watchHistory.setEpisode(episode);
        }
        return convertToDTO(watchHistoryRepository.save(watchHistory));
    }


    private WatchHistoryDTO convertToDTO(WatchHistory watchHistory) {
        WatchHistoryDTO dto = modelMapper.map(watchHistory, WatchHistoryDTO.class);
        dto.setMovieId(watchHistory.getMovie().getId());
        dto.setMovieTitle(watchHistory.getMovie().getTitle());
        dto.setPosterUrl(watchHistory.getMovie().getPosterUrl());
        if (watchHistory.getEpisode() != null) {
            dto.setEpisodeId(watchHistory.getEpisode().getId());
            dto.setEpisodeNumber(watchHistory.getEpisode().getEpisodeNumber());
        }

        return dto;
    }
}
