package com.behindthesmile.posting.service;

import com.behindthesmile.posting.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AppPathService {
    private final AppProperties appProperties;

    public AppPathService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public Path draftPath() {
        return dataPath(appProperties.runtime().draftsFile());
    }

    public Path queuePath() {
        return dataPath(appProperties.runtime().queueFile());
    }

    public Path xLinksPath() {
        return dataPath(appProperties.runtime().xLinksFile());
    }

    public Path contentPlanPath() {
        return Path.of(appProperties.runtime().contentPlanFile()).toAbsolutePath().normalize();
    }

    public Path dataDir() {
        return Path.of(appProperties.runtime().dataDir()).toAbsolutePath().normalize();
    }

    public Map<String, Object> healthDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        Path dataDir = dataDir();
        Path contentPlanPath = contentPlanPath();
        details.put("dataDir", dataDir.toString());
        details.put("dataDirWritable", isWritableDirectory(dataDir));
        details.put("queuePath", queuePath().toString());
        details.put("draftPath", draftPath().toString());
        details.put("xLinksPath", xLinksPath().toString());
        details.put("contentPlanPath", contentPlanPath.toString());
        details.put("contentPlanExists", Files.exists(contentPlanPath));
        return details;
    }

    private Path dataPath(String fileName) {
        return dataDir().resolve(fileName).normalize();
    }

    private boolean isWritableDirectory(Path path) {
        try {
            Files.createDirectories(path);
            return Files.isDirectory(path) && Files.isWritable(path);
        } catch (IOException ignored) {
            return false;
        }
    }
}
