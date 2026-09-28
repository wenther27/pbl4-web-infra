package com.example.cinehub;

import com.example.cinehub.exception.BadRequestException;
import com.example.cinehub.service.ImageStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageStorageServiceTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesValidPngWithGeneratedName() throws Exception {
        ImageStorageService service = new ImageStorageService(temporaryDirectory.toString(), 1024);
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", png);

        ImageStorageService.StoredImage stored = service.store(file, "movies");

        assertEquals("movies", stored.category());
        assertTrue(stored.fileName().endsWith(".png"));
        assertTrue(Files.exists(temporaryDirectory.resolve("movies").resolve(stored.fileName())));
    }

    @Test
    void rejectsFileWhoseContentDoesNotMatchImageType() {
        ImageStorageService service = new ImageStorageService(temporaryDirectory.toString(), 1024);
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes());

        assertThrows(BadRequestException.class, () -> service.store(file, "movies"));
    }

    @Test
    void rejectsOversizedImage() {
        ImageStorageService service = new ImageStorageService(temporaryDirectory.toString(), 4);
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png", png);

        assertThrows(BadRequestException.class, () -> service.store(file, "movies"));
    }
}
