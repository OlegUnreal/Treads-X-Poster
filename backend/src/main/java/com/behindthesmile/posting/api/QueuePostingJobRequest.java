package com.behindthesmile.posting.api;

public record QueuePostingJobRequest(
        String accountId,
        String platform,
        Integer intervalHours,
        Integer postsPerRun,
        Integer minimumReady,
        Boolean randomizeUpToHour
) {
}