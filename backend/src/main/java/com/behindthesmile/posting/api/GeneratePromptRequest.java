package com.behindthesmile.posting.api;

import java.util.List;

public record GeneratePromptRequest(
        String prompt,
        String topic,
        String language,
        Integer count,
        List<String> platforms,
        List<String> accountIds,
        List<String> targetProfiles,
        boolean saveToQueue
) {
}
