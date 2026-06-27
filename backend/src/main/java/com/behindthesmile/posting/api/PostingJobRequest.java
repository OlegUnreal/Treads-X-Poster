package com.behindthesmile.posting.api;

public record PostingJobRequest(
        Integer intervalHours,
        Integer threadsPerRun,
        Integer xPerRun,
        Integer minimumReady,
        Boolean randomizeUpToHour
) {
}
