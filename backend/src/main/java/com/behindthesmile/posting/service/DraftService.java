package com.behindthesmile.posting.service;

import com.behindthesmile.posting.model.GeneratedPostDraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DraftService {
    private final ObjectMapper objectMapper;

    public DraftService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Path saveDraft(List<GeneratedPostDraft> posts, String topic, Path filePath) throws IOException {
        Files.createDirectories(filePath.toAbsolutePath().getParent());
        String now = Instant.now().toString();
        List<String> lines = new ArrayList<>();

        for (GeneratedPostDraft post : posts) {
            lines.add(objectMapper.writeValueAsString(Map.of(
                    "createdAt", now,
                    "topic", topic,
                    "text", post.getText(),
                    "visualHint", post.getVisualHint()
            )));
        }

        Files.writeString(
                filePath,
                String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        return filePath.toAbsolutePath();
    }
}
