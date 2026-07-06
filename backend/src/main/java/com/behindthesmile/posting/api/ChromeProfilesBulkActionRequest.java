package com.behindthesmile.posting.api;

import java.util.List;

public record ChromeProfilesBulkActionRequest(
        String action,
        List<String> profileNames,
        String url,
        Integer minDelaySeconds,
        Integer maxDelaySeconds,
        String referer,
        String videoQuality
) {
}
