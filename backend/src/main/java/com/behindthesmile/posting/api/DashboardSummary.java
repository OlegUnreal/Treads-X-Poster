package com.behindthesmile.posting.api;

public record DashboardSummary(
        long queueReady,
        long threadsReady,
        long xReady,
        String postingStatus,
        String lastDailyMessage,
        String lastThreadsMessage,
        PublisherAccountSummary publisherAccounts,
        PostingJobStatus jobStatus
) {
}
