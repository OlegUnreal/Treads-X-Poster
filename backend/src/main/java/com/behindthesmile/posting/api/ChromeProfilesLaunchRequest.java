package com.behindthesmile.posting.api;

public record ChromeProfilesLaunchRequest(
        Integer minDelaySeconds,
        Integer maxDelaySeconds
) {
}
