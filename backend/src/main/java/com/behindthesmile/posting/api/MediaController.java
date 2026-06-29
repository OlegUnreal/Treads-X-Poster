package com.behindthesmile.posting.api;

import com.behindthesmile.posting.service.AppPathService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final AppPathService appPathService;

    public MediaController(AppPathService appPathService) {
        this.appPathService = appPathService;
    }

    @GetMapping("/queue-images/{fileName:.+}")
    public ResponseEntity<Resource> queueImage(@PathVariable String fileName) throws Exception {
        Path path = appPathService.queueImagePath(fileName);
        Path root = appPathService.queueImagesDir();
        if (!path.startsWith(root) || !Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(path);
        MediaType mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new FileSystemResource(path));
    }
}
