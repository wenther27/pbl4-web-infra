package com.example.cinehub.service;

import com.example.cinehub.dto.response.EpisodeDTO;
import com.example.cinehub.entity.Episode;

import java.util.List;

public interface EpisodeService {
    List<EpisodeDTO> getEpisodeByMovieId(Long id);
    EpisodeDTO addEpisode(Long id, Episode episode);
    EpisodeDTO updateEpisode(Long id, Episode episodeDetails);
    void deleteEpisode(Long id);
}
