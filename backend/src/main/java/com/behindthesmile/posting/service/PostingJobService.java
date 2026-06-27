package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.ActionResult;
import com.behindthesmile.posting.api.PostingJobRequest;
import com.behindthesmile.posting.api.PostingJobStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class PostingJobService {
    private static final long MIN_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final PostingJobRequest DEFAULT_CONFIG = new PostingJobRequest(4, 1, 1, 8, false);

    private final SocialPostingService socialPostingService;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> scheduledFuture;
    private PostingJobRequest activeConfig;
    private boolean running;
    private Instant startedAt;
    private Instant lastHeartbeatAt;
    private Instant nextRunAt;
    private Integer nextRunOffsetMinutes;
    private String lastRunMessage = "Job has not run yet.";

    public PostingJobService(SocialPostingService socialPostingService) {
        this.socialPostingService = socialPostingService;
    }

    public synchronized ActionResult start(PostingJobRequest request) {
        if (running) {
            return new ActionResult(false, "start-posting-job", "Posting job is already running.");
        }

        PostingJobRequest normalized = normalize(request);
        activeConfig = normalized;
        running = true;
        startedAt = Instant.now();
        lastHeartbeatAt = null;
        lastRunMessage = "Job scheduled.";
        scheduleNextRun(computeNextDelayMillis(normalized), currentOffsetMinutes);

        return new ActionResult(true, "start-posting-job",
                "Posting job started. First run is scheduled in " + normalized.intervalHours() + " hour(s)"
                        + (Boolean.TRUE.equals(normalized.randomizeUpToHour()) ? " with up to 1 hour random shift." : "."));
    }

    public synchronized ActionResult update(PostingJobRequest request) {
        PostingJobRequest normalized = normalize(request);
        activeConfig = normalized;

        if (!running) {
            nextRunAt = null;
            nextRunOffsetMinutes = null;
            lastRunMessage = "Job settings updated.";
            return new ActionResult(true, "update-posting-job", "Job settings updated. Start the job when you are ready.");
        }

        if (nextRunAt != null && scheduledFuture != null && !scheduledFuture.isDone() && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false);
            scheduleNextRun(computeNextDelayMillis(normalized), currentOffsetMinutes);
            lastRunMessage = "Job settings updated. Next run rescheduled.";
            return new ActionResult(true, "update-posting-job", "Job settings updated and the next run was rescheduled.");
        }

        lastRunMessage = "Job settings updated. Changes will apply after the current run finishes.";
        return new ActionResult(true, "update-posting-job", "Job settings updated. Changes will apply after the current run finishes.");
    }

    public synchronized ActionResult stop() {
        if (!running) {
            return new ActionResult(false, "stop-posting-job", "Posting job is not running.");
        }

        running = false;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
        scheduledFuture = null;
        lastHeartbeatAt = Instant.now();
        nextRunAt = null;
        nextRunOffsetMinutes = null;
        lastRunMessage = "Job stopped.";
        return new ActionResult(true, "stop-posting-job", "Posting job stopped.");
    }

    public synchronized PostingJobStatus status() {
        return new PostingJobStatus(
                running,
                effectiveConfig().intervalHours(),
                effectiveConfig().threadsPerRun(),
                effectiveConfig().xPerRun(),
                effectiveConfig().minimumReady(),
                effectiveConfig().randomizeUpToHour(),
                startedAt == null ? null : startedAt.toString(),
                lastHeartbeatAt == null ? null : lastHeartbeatAt.toString(),
                nextRunAt == null ? null : nextRunAt.toString(),
                nextRunOffsetMinutes,
                lastRunMessage
        );
    }

    private void runSafelyAndReschedule() {
        runSafely();

        synchronized (this) {
            if (!running || activeConfig == null) {
                return;
            }
        }

        scheduleNextRun(computeNextDelayMillis(activeConfig), currentOffsetMinutes);
    }

    private void runSafely() {
        synchronized (this) {
            lastHeartbeatAt = Instant.now();
            nextRunAt = null;
            nextRunOffsetMinutes = null;
        }

        try {
            String result = socialPostingService.daily(Map.of(
                    "threadsPerRun", String.valueOf(activeConfig.threadsPerRun()),
                    "xPerRun", String.valueOf(activeConfig.xPerRun()),
                    "minimumReady", String.valueOf(activeConfig.minimumReady())
            ));
            synchronized (this) {
                lastHeartbeatAt = Instant.now();
                lastRunMessage = result;
            }
        } catch (Exception ex) {
            synchronized (this) {
                lastHeartbeatAt = Instant.now();
                lastRunMessage = "Job run failed: " + ex.getMessage();
            }
        }
    }

    private int currentOffsetMinutes;

    private synchronized void scheduleNextRun(long delayMillis, int offsetMinutes) {
        if (!running) {
            return;
        }

        nextRunAt = Instant.now().plusMillis(delayMillis);
        nextRunOffsetMinutes = delayMillis == 0 ? 0 : offsetMinutes;
        scheduledFuture = executor.schedule(this::runSafelyAndReschedule, delayMillis, TimeUnit.MILLISECONDS);
    }

    private long computeNextDelayMillis(PostingJobRequest config) {
        int offsetMinutes = 0;
        if (Boolean.TRUE.equals(config.randomizeUpToHour())) {
            offsetMinutes = ThreadLocalRandom.current().nextInt(-60, 61);
        }
        currentOffsetMinutes = offsetMinutes;

        long baseDelayMillis = TimeUnit.HOURS.toMillis(config.intervalHours());
        long jitterMillis = TimeUnit.MINUTES.toMillis(offsetMinutes);
        return Math.max(MIN_DELAY_MILLIS, baseDelayMillis + jitterMillis);
    }

    private PostingJobRequest effectiveConfig() {
        return activeConfig == null ? DEFAULT_CONFIG : activeConfig;
    }

    private PostingJobRequest normalize(PostingJobRequest request) {
        PostingJobRequest base = request == null ? effectiveConfig() : request;
        int intervalHours = base.intervalHours() == null || base.intervalHours() < 1 ? effectiveConfig().intervalHours() : base.intervalHours();
        int threadsPerRun = base.threadsPerRun() == null || base.threadsPerRun() < 0 ? effectiveConfig().threadsPerRun() : base.threadsPerRun();
        int xPerRun = base.xPerRun() == null || base.xPerRun() < 0 ? effectiveConfig().xPerRun() : base.xPerRun();
        int minimumReady = base.minimumReady() == null || base.minimumReady() < 1 ? effectiveConfig().minimumReady() : base.minimumReady();
        boolean randomizeUpToHour = Boolean.TRUE.equals(base.randomizeUpToHour());
        return new PostingJobRequest(intervalHours, threadsPerRun, xPerRun, minimumReady, randomizeUpToHour);
    }
}
