package com.example.cinehub.controller;

import com.example.cinehub.dto.request.EpisodeRequest;
import com.example.cinehub.dto.response.EpisodeDTO;
import com.example.cinehub.service.EpisodeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class EpisodeController {

    @Autowired private EpisodeService episodeService;

    // 1. Danh sach tap cua mot phim (public)
    @GetMapping("/movies/{movieId}/episodes")
    public ResponseEntity<List<EpisodeDTO>> getEpisodeByMovieId(@PathVariable Long movieId) {
        return ResponseEntity.ok(episodeService.getEpisodeByMovieId(movieId));
    }

    // 2. Them tap phim (ADMIN)
    @PostMapping("/movies/{movieId}/episodes")
    public ResponseEntity<EpisodeDTO> addEpisode(@PathVariable Long movieId,
                                                 @Valid @RequestBody EpisodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(episodeService.addEpisode(movieId, request));
    }

    // 3. Sua tap phim (ADMIN)
    @PutMapping("/episodes/{episodeId}")
    public ResponseEntity<EpisodeDTO> updateEpisode(@PathVariable Long episodeId,
                                                    @Valid @RequestBody EpisodeRequest request) {
        return ResponseEntity.ok(episodeService.updateEpisode(episodeId, request));
    }

    // 4. Xoa tap phim (ADMIN)
    @DeleteMapping("/episodes/{episodeId}")
    public ResponseEntity<Void> deleteEpisode(@PathVariable Long episodeId) {
        episodeService.deleteEpisode(episodeId);
        return ResponseEntity.noContent().build();
    }
}
