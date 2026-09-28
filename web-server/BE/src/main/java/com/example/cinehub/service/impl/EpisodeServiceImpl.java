package com.example.cinehub.service.impl;

import com.example.cinehub.dto.request.EpisodeRequest;
import com.example.cinehub.dto.response.EpisodeDTO;
import com.example.cinehub.entity.Episode;
import com.example.cinehub.entity.Movie;
import com.example.cinehub.exception.BadRequestException;
import com.example.cinehub.exception.ResourceNotFoundException;
import com.example.cinehub.repository.EpisodeRepository;
import com.example.cinehub.repository.MovieRepository;
import com.example.cinehub.service.EpisodeService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class EpisodeServiceImpl implements EpisodeService {
    @Autowired private EpisodeRepository episodeRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private ModelMapper modelMapper;

    @Transactional(readOnly = true)
    @Override
    public List<EpisodeDTO> getEpisodeByMovieId(Long movieId) {
        if (!movieRepository.existsById(movieId))
            throw new ResourceNotFoundException("Không tìm thấy phim với ID = " + movieId);

        return episodeRepository.findByMovieIdOrderByEpisodeNumberAsc(movieId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public EpisodeDTO addEpisode(Long movieId, EpisodeRequest request) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phim với ID = " + movieId));

        if (episodeRepository.existsByMovieIdAndEpisodeNumber(movieId, request.getEpisodeNumber()))
            throw new BadRequestException("Phim này đã có tập số " + request.getEpisodeNumber());

        Episode episode = new Episode();
        applyRequest(episode, request);
        episode.setMovie(movie); // khoa ngoai noi tap phim voi bo phim

        return convertToDTO(episodeRepository.save(episode));
    }

    @Transactional
    @Override
    public EpisodeDTO updateEpisode(Long episodeId, EpisodeRequest request) {
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tập phim với ID = " + episodeId));

        // Chi check trung khi nguoi dung doi sang so tap khac
        Long movieId = episode.getMovie().getId();
        if (!Objects.equals(episode.getEpisodeNumber(), request.getEpisodeNumber())
                && episodeRepository.existsByMovieIdAndEpisodeNumber(movieId, request.getEpisodeNumber()))
            throw new BadRequestException("Phim này đã có tập số " + request.getEpisodeNumber());

        applyRequest(episode, request);
        return convertToDTO(episodeRepository.save(episode));
    }

    @Transactional
    @Override
    public void deleteEpisode(Long episodeId) {
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tập phim với ID = " + episodeId));
        episodeRepository.delete(episode);
    }

    private void applyRequest(Episode episode, EpisodeRequest request) {
        episode.setTitle(request.getTitle());
        episode.setEpisodeNumber(request.getEpisodeNumber());
        episode.setVideoUrl(request.getVideoUrl());
        episode.setThumbnailUrl(request.getThumbnailUrl());
        episode.setDuration(request.getDuration());
    }

    private EpisodeDTO convertToDTO(Episode episode) {
        return modelMapper.map(episode, EpisodeDTO.class);
    }
}
