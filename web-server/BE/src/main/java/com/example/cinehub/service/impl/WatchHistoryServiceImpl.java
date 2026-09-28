package com.example.cinehub.service.impl;

import com.example.cinehub.dto.request.WatchHistoryRequest;
import com.example.cinehub.dto.response.WatchHistoryDTO;
import com.example.cinehub.entity.Episode;
import com.example.cinehub.entity.Movie;
import com.example.cinehub.entity.User;
import com.example.cinehub.entity.WatchHistory;
import com.example.cinehub.exception.BadRequestException;
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
        return watchHistoryRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public WatchHistoryDTO saveOrUpdateHistory(Long userId, WatchHistoryRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với ID = " + userId));
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phim với ID = " + request.getMovieId()));

        // Upsert: tim record cu neu co, khong co thi tao moi.
        // Tranh tao nhieu record trung nhau cho cung 1 user + phim.
        WatchHistory watchHistory = watchHistoryRepository
                .findByUserIdAndMovieId(userId, request.getMovieId())
                .orElseGet(WatchHistory::new);

        watchHistory.setUser(user);
        watchHistory.setMovie(movie);
        watchHistory.setLastPosition(request.getLastPosition());

        if (request.getEpisodeId() != null) {
            Episode episode = episodeRepository.findById(request.getEpisodeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy tập phim với ID = " + request.getEpisodeId()));

            // Chan truong hop FE gui tap phim thuoc bo phim khac
            if (!episode.getMovie().getId().equals(request.getMovieId()))
                throw new BadRequestException("Tập phim này không thuộc bộ phim đã chọn");

            watchHistory.setEpisode(episode);
        } else {
            // Xem lai tu dau (phim le) -> xoa tap phim da luu truoc do
            watchHistory.setEpisode(null);
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
