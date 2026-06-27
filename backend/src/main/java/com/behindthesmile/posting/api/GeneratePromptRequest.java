package com.behindthesmile.posting.api;

import java.util.List;

public record GeneratePromptRequest(
        String prompt,
        String topic,
        String tone,
        String language,
        Integer count,
        List<String> platforms,
        boolean saveToQueue
) {
}
