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
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {
    private static final Set<String> ALLOWED_CATEGORIES = Set.of("movies", "episodes", "avatars");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );

    private final Path uploadRoot;
    private final long maximumFileSize;

    public ImageStorageService(
            @Value("${app.upload.directory:uploads}") String uploadDirectory,
            @Value("${app.upload.max-size-bytes:5242880}") long maximumFileSize) {
        this.uploadRoot = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.maximumFileSize = maximumFileSize;
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể khởi tạo thư mục lưu ảnh", exception);
        }
    }

    public StoredImage store(MultipartFile file, String requestedCategory) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn một file ảnh");
        }
        if (file.getSize() > maximumFileSize) {
            throw new BadRequestException("Ảnh vượt quá dung lượng tối đa 5 MB");
        }

        String contentType = normalizeContentType(file.getContentType());
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BadRequestException("Chỉ hỗ trợ ảnh JPG, PNG, WebP hoặc GIF");
        }

        String category = ALLOWED_CATEGORIES.contains(requestedCategory) ? requestedCategory : "movies";
        String fileName = UUID.randomUUID() + extension;
        Path categoryDirectory = uploadRoot.resolve(category).normalize();
        Path destination = categoryDirectory.resolve(fileName).normalize();
        if (!destination.startsWith(uploadRoot)) {
            throw new BadRequestException("Đường dẫn lưu ảnh không hợp lệ");
        }

        try {
            byte[] header = readHeader(file);
            if (!matchesSignature(contentType, header)) {
                throw new BadRequestException("Nội dung file không đúng định dạng ảnh");
            }
            Files.createDirectories(categoryDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (BadRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể lưu file ảnh", exception);
        }

        return new StoredImage(category, fileName, contentType, file.getSize());
    }

    public Path getUploadRoot() {
        return uploadRoot;
    }

    private String normalizeContentType(String contentType) {
        if ("image/jpg".equalsIgnoreCase(contentType)) return "image/jpeg";
        return contentType == null ? "" : contentType.toLowerCase();
    }

    private byte[] readHeader(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(12);
        }
    }

    private boolean matchesSignature(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && unsigned(bytes[0]) == 0xFF && unsigned(bytes[1]) == 0xD8 && unsigned(bytes[2]) == 0xFF;
            case "image/png" -> bytes.length >= 8
                    && unsigned(bytes[0]) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                    && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            case "image/gif" -> bytes.length >= 6
                    && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8'
                    && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
            default -> false;
        };
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    public record StoredImage(String category, String fileName, String contentType, long size) {
    }
}
