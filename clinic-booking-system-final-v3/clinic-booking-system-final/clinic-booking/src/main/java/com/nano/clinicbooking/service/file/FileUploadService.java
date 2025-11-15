package com.nano.clinicbooking.service.file;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileUploadService {

    private final Path rootPath = Paths.get(System.getProperty("user.dir"))
            .getParent()
            .resolve("frontend")
            .resolve("public")
            .resolve("uploads")
            .toAbsolutePath();

    private final String BASE_URL = "http://localhost:8080/uploads/";

    public FileUploadService() {
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize upload directory", e);
        }
    }

    /**
     * Validate image file
     */
    public void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Image cannot be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.matches("image/(png|jpg|jpeg|webp)")) {
            throw new RuntimeException("Invalid image format. Only PNG, JPG, JPEG, WEBP are allowed");
        }

        if (file.getSize() > 5 * 1024 * 1024) { // 5MB
            throw new RuntimeException("Image exceeds 5MB");
        }
    }

    /**
     * Upload file and return URL
     */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        validateImage(file);

        Path folderPath = rootPath.resolve(folder);
        Files.createDirectories(folderPath);

        String fileName = UUID.randomUUID() + "_" +
                StringUtils.cleanPath(file.getOriginalFilename());

        Path filePath = folderPath.resolve(fileName);
        file.transferTo(filePath.toFile());

        return BASE_URL + folder + "/" + fileName;
    }

    /**
     * Delete old file if exists
     */
    public void deleteFile(String url) {
        try {
            if (url == null || url.isEmpty()) return;

            String fileName = url.replace(BASE_URL, "");
            Path oldFile = rootPath.resolve(fileName);

            if (Files.exists(oldFile)) {
                Files.delete(oldFile);
            }
        } catch (Exception ignored) {
            // Ignore deletion errors
        }
    }
}

