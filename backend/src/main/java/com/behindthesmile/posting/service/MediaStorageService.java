package com.behindthesmile.posting.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MediaStorageService {
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    private final HttpService httpService;
    private final AppPathService appPathService;

    public MediaStorageService(HttpService httpService, AppPathService appPathService) {
        this.httpService = httpService;
        this.appPathService = appPathService;
    }

    public String storeRemoteQueueImage(String imageUrl, String idHint) throws IOException, InterruptedException {
        if (imageUrl == null || imageUrl.isBlank()) {
            return imageUrl;
        }

        String trimmed = imageUrl.trim();
        if (trimmed.startsWith(appPathService.publicBaseUrl() + "/api/media/")) {
            return trimmed;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(trimmed))
                .header("Accept", "image/*")
                .header("User-Agent", "BehindTheSmileBot/1.0 media storage")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpService.client().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Image download failed with status " + response.statusCode());
        }
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            throw new IOException("Image download returned an empty file.");
        }
        if (body.length > MAX_IMAGE_BYTES) {
            throw new IOException("Image is larger than " + MAX_IMAGE_BYTES + " bytes.");
        }

        String extension = extension(response, trimmed);
        String fileName = sanitize(idHint) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + extension;
        Path target = appPathService.queueImagePath(fileName);
        Files.createDirectories(target.getParent());
        Files.write(target, body, StandardOpenOption.CREATE_NEW);
        return appPathService.publicBaseUrl() + "/api/media/queue-images/" + fileName;
    }

    public String storeQueueImageBytes(byte[] body, String extension, String idHint) throws IOException {
        if (body == null || body.length == 0) {
            throw new IOException("Generated image returned an empty file.");
        }
        if (body.length > MAX_IMAGE_BYTES) {
            throw new IOException("Generated image is larger than " + MAX_IMAGE_BYTES + " bytes.");
        }

        String safeExtension = extension == null || extension.isBlank() ? ".jpg" : extension.trim().toLowerCase(Locale.ROOT);
        if (!safeExtension.startsWith(".")) {
            safeExtension = "." + safeExtension;
        }
        if (!List.of(".jpg", ".jpeg", ".png", ".webp").contains(safeExtension)) {
            safeExtension = ".jpg";
        }

        String fileName = sanitize(idHint) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + safeExtension;
        Path target = appPathService.queueImagePath(fileName);
        Files.createDirectories(target.getParent());
        Files.write(target, body, StandardOpenOption.CREATE_NEW);
        return appPathService.publicBaseUrl() + "/api/media/queue-images/" + fileName;
    }

    private String extension(HttpResponse<?> response, String imageUrl) {
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        if (contentType.contains("png")) {
            return ".png";
        }
        if (contentType.contains("webp")) {
            return ".webp";
        }
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return ".jpg";
        }

        String normalized = imageUrl.toLowerCase(Locale.ROOT);
        if (normalized.contains(".png")) {
            return ".png";
        }
        if (normalized.contains(".webp")) {
            return ".webp";
        }
        return ".jpg";
    }

    private String sanitize(String value) {
        String sanitized = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        if (sanitized.isBlank()) {
            return "queue-image";
        }
        return sanitized.length() > 48 ? sanitized.substring(0, 48).replaceAll("-+$", "") : sanitized;
    }
}
