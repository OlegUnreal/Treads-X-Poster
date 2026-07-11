package com.behindthesmile.posting.api;

public record QueuePostingJobSummary(
        String id,
        String accountId,
        String accountLabel,
        String platform,
        boolean running,
        Integer intervalHours,
        Integer postsPerRun,
        Integer minimumReady,
        Boolean randomizeUpToHour,
        String startedAt,
        String lastHeartbeatAt,
        String nextRunAt,
        Integer nextRunOffsetMinutes,
        String lastRunMessage
) {
}