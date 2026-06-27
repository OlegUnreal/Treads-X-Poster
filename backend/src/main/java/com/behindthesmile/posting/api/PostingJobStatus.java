package com.behindthesmile.posting.api;

public record PostingJobStatus(
        boolean running,
        Integer intervalHours,
        Integer threadsPerRun,
        Integer xPerRun,
        Integer minimumReady,
        Boolean randomizeUpToHour,
        String startedAt,
        String lastHeartbeatAt,
        String nextRunAt,
        Integer nextRunOffsetMinutes,
        String lastRunMessage
) {
}
