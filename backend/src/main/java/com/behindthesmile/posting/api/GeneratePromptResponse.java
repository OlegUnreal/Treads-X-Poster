package com.behindthesmile.posting.api;

import java.util.List;

public record GeneratePromptResponse(
        List<String> posts,
        boolean savedToQueue,
        String message
) {
}
