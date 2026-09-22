package com.example.cinehub.controller;

import com.example.cinehub.dto.response.EpisodeDTO;
import com.example.cinehub.entity.Episode;
import com.example.cinehub.service.EpisodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class EpisodeController {

    @Autowired
    private EpisodeService episodeService;

    @GetMapping("/movies/{movieId}/episodes")
    public ResponseEntity<List<EpisodeDTO>> getEpisodeByMovieId(@PathVariable Long movieId) { // 🌟 Sửa 'id' thành 'movieId' cho khớp URL
        return ResponseEntity.ok(episodeService.getEpisodeByMovieId(movieId));
    }

    // 2. Thêm tập phim mới
    @PostMapping("/movies/{movieId}/episodes")
    public ResponseEntity<EpisodeDTO> addEpisode(@PathVariable Long movieId, @RequestBody Episode episode) { // 🌟 Sửa 'id' thành 'movieId'
        return ResponseEntity.ok(episodeService.addEpisode(movieId, episode));
    }

    // 3. Sửa tập phim
    @PutMapping("/episodes/{episodeId}")
    public ResponseEntity<EpisodeDTO> updateEpisode(@PathVariable Long episodeId, @RequestBody Episode episodeDetails) { // 🌟 Sửa 'id' thành 'episodeId'
        return ResponseEntity.ok(episodeService.updateEpisode(episodeId, episodeDetails));
    }

    // 4. Xóa tập phim
    @DeleteMapping("/episodes/{episodeId}")
    public ResponseEntity<String> deleteEpisode(@PathVariable Long episodeId) { // 🌟 Sửa 'id' thành 'episodeId'
        episodeService.deleteEpisode(episodeId);
        return ResponseEntity.ok("Đã xóa tập film thành công");
    }
}