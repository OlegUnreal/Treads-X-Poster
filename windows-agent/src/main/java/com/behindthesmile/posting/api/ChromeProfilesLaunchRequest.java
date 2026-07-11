package com.behindthesmile.posting.api;

import java.util.List;

public record ChromeProfilesLaunchRequest(
        Integer minDelaySeconds,
        Integer maxDelaySeconds,
        Integer profileCount,
        String url,
        List<String> profileNames,
        Boolean loginMode,
        String referer,
        String videoQuality,
        Boolean requireYoutube,
        Boolean requirePornhub
) {
}
