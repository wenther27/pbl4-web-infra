package com.example.cinehub;

import com.example.cinehub.exception.BadRequestException;
import com.example.cinehub.service.VideoStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoStorageServiceTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesValidMp4WithGeneratedName() throws Exception {
        VideoStorageService service = new VideoStorageService(temporaryDirectory.toString(), 1024);
        byte[] mp4 = new byte[] {0, 0, 0, 12, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        MockMultipartFile file = new MockMultipartFile("file", "episode.mp4", "video/mp4", mp4);

        VideoStorageService.StoredVideo stored = service.store(file);

        assertEquals("video/mp4", stored.contentType());
        assertTrue(stored.fileName().endsWith(".mp4"));
        assertTrue(Files.exists(temporaryDirectory.resolve("videos").resolve(stored.fileName())));
    }

    @Test
    void rejectsFileWhoseContentDoesNotMatchVideoType() {
        VideoStorageService service = new VideoStorageService(temporaryDirectory.toString(), 1024);
        MockMultipartFile file = new MockMultipartFile("file", "fake.mp4", "video/mp4", "not-a-video".getBytes());

        assertThrows(BadRequestException.class, () -> service.store(file));
    }

    @Test
    void rejectsOversizedVideo() {
        VideoStorageService service = new VideoStorageService(temporaryDirectory.toString(), 4);
        byte[] mp4 = new byte[] {0, 0, 0, 12, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        MockMultipartFile file = new MockMultipartFile("file", "large.mp4", "video/mp4", mp4);

        assertThrows(BadRequestException.class, () -> service.store(file));
    }
}
