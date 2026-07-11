package com.behindthesmile.posting.service;

import com.behindthesmile.posting.model.GeneratedPostDraft;
import com.behindthesmile.posting.persistence.DraftEntity;
import com.behindthesmile.posting.persistence.DraftRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class DraftService {
    private final DraftRepository draftRepository;
    private final AppPathService appPathService;
    private final ObjectMapper objectMapper;

    public DraftService(
            DraftRepository draftRepository,
            AppPathService appPathService,
            ObjectMapper objectMapper
    ) {
        this.draftRepository = draftRepository;
        this.appPathService = appPathService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void migrateLegacyDraftFile() {
        Path path = appPathService.draftPath();
        if (!Files.exists(path)) {
            return;
        }

        try {
            List<LegacyDraft> legacyDrafts = Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .map(this::readLegacyDraft)
                    .filter(Objects::nonNull)
                    .toList();

            if (legacyDrafts.isEmpty()) {
                return;
            }

            for (LegacyDraft legacyDraft : legacyDrafts) {
                String text = legacyDraft.text();
                if (text == null || text.isBlank()) {
                    continue;
                }
                draftRepository.save(new DraftEntity(
                        Objects.requireNonNullElse(legacyDraft.createdAt(), Instant.now().toString()),
                        legacyDraft.topic(),
                        text,
                        legacyDraft.visualHint()
                ));
            }
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // If legacy migration fails, keep running with database-backed state.
        }
    }

    private LegacyDraft readLegacyDraft(String value) {
        try {
            return objectMapper.readValue(value, LegacyDraft.class);
        } catch (IOException ignored) {
            return null;
        }
    }

    public Path saveDraft(List<GeneratedPostDraft> posts, String topic, Path filePath) {
        String now = Instant.now().toString();

        for (GeneratedPostDraft post : posts) {
            String visualHint = post == null ? null : post.getVisualHint();
            String text = post == null ? null : post.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            draftRepository.save(new DraftEntity(now, topic, text, visualHint));
        }
        return filePath.toAbsolutePath();
    }

    private record LegacyDraft(String createdAt, String topic, String text, String visualHint) {
    }
}
