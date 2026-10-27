package com.example.cinehub.service;

import com.example.cinehub.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
public class VideoStorageService {
    private static final Map<String, String> EXTENSIONS = Map.of(
            "video/mp4", ".mp4",
            "video/webm", ".webm",
            "video/ogg", ".ogv"
    );

    private final Path videoDirectory;
    private final long maximumFileSize;

    public VideoStorageService(
            @Value("${app.upload.directory:uploads}") String uploadDirectory,
            @Value("${app.upload.video-max-size-bytes:524288000}") long maximumFileSize) {
        Path uploadRoot = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.videoDirectory = uploadRoot.resolve("videos").normalize();
        this.maximumFileSize = maximumFileSize;
        try {
            Files.createDirectories(videoDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể khởi tạo thư mục lưu video", exception);
        }
    }

    public StoredVideo store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn một file video");
        }
        if (file.getSize() > maximumFileSize) {
            throw new BadRequestException("Video vượt quá dung lượng tối đa 500 MB");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BadRequestException("Chỉ hỗ trợ video MP4, WebM hoặc OGG");
        }

        String fileName = UUID.randomUUID() + extension;
        Path destination = videoDirectory.resolve(fileName).normalize();
        if (!destination.startsWith(videoDirectory)) {
            throw new BadRequestException("Đường dẫn lưu video không hợp lệ");
        }

        try {
            byte[] header = readHeader(file);
            if (!matchesSignature(contentType, header)) {
                throw new BadRequestException("Nội dung file không đúng định dạng video");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (BadRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể lưu file video", exception);
        }

        return new StoredVideo(fileName, contentType, file.getSize());
    }

    private byte[] readHeader(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(12);
        }
    }

    private boolean matchesSignature(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "video/mp4" -> bytes.length >= 8
                    && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p';
            case "video/webm" -> bytes.length >= 4
                    && unsigned(bytes[0]) == 0x1A && unsigned(bytes[1]) == 0x45
                    && unsigned(bytes[2]) == 0xDF && unsigned(bytes[3]) == 0xA3;
            case "video/ogg" -> bytes.length >= 4
                    && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S';
            default -> false;
        };
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    public record StoredVideo(String fileName, String contentType, long size) {
    }
}
