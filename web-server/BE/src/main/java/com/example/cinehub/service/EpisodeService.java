package com.example.cinehub.service;

import com.example.cinehub.dto.request.EpisodeRequest;
import com.example.cinehub.dto.response.EpisodeDTO;

import java.util.List;

public interface EpisodeService {
    List<EpisodeDTO> getEpisodeByMovieId(Long movieId);
    EpisodeDTO addEpisode(Long movieId, EpisodeRequest request);
    EpisodeDTO updateEpisode(Long episodeId, EpisodeRequest request);
    void deleteEpisode(Long episodeId);
}
