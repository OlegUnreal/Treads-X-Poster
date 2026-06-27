package com.behindthesmile.posting.service;

import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class AppPathService {
    public Path draftPath() {
        return Path.of("../generated/posts.jsonl");
    }

    public Path queuePath() {
        return Path.of("../generated/queue.jsonl");
    }

    public Path xLinksPath() {
        return Path.of("../generated/x-ready.html");
    }

    public Path contentPlanPath() {
        return Path.of("config/content-plan.json");
    }
}
