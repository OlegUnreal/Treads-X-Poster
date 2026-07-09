package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.ActionResult;
import com.behindthesmile.posting.api.PostingJobRequest;
import com.behindthesmile.posting.api.PostingJobStatus;
import com.behindthesmile.posting.api.QueuePostingJobRequest;
import com.behindthesmile.posting.api.QueuePostingJobSummary;
import com.behindthesmile.posting.persistence.PostingJobEntity;
import com.behindthesmile.posting.persistence.PostingJobRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class PostingJobService {
    private static final long MIN_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final PostingJobRequest DEFAULT_LEGACY_CONFIG = new PostingJobRequest(4, 1, 1, 8, false);
    private static final QueuePostingJobRequest DEFAULT_JOB_CONFIG = new QueuePostingJobRequest(
            "",
            "x",
            4,
            1,
            8,
            false
    );

    private final SocialPostingService socialPostingService;
    private final PostingJobRepository postingJobRepository;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<String, JobState> jobs = new LinkedHashMap<>();
    private final Map<String, PostingJobRequest> legacyState = new LinkedHashMap<>();
    private final Map<String, ScheduledFuture<?>> runtimeState = new ConcurrentHashMap<>();

    public PostingJobService(
            SocialPostingService socialPostingService,
            PostingJobRepository postingJobRepository
    ) {
        this.socialPostingService = socialPostingService;
        this.postingJobRepository = postingJobRepository;
    }

    @PostConstruct
    private void initializeFromDatabase() {
        postingJobRepository.findAllByOrderByAccountIdAscPlatformAsc().forEach(entity -> {
            JobState restored = toState(entity);
            jobs.put(restored.id, restored);
            if (restored.running) {
                startRuntimeIfConfigured(restored);
            }
        });
    }

    public synchronized List<QueuePostingJobSummary> list() {
        return jobs.values().stream()
                .map(this::toSummary)
                .sorted(Comparator.comparing(QueuePostingJobSummary::accountId).thenComparing(QueuePostingJobSummary::platform))
                .toList();
    }

    public synchronized QueuePostingJobSummary create(QueuePostingJobRequest request) {
        QueuePostingJobRequest normalized = normalizeQueueRequest(request);
        String id = UUID.randomUUID().toString();
        JobState state = new JobState(
                id,
                normalized.accountId(),
                normalized.platform(),
                normalized.intervalHours(),
                normalized.postsPerRun(),
                normalized.minimumReady(),
                Boolean.TRUE.equals(normalized.randomizeUpToHour())
        );
        socialPostingService.requireAccountForJobTargets(state.accountId);
        jobs.put(id, state);
        persistQueueState(state);
        return toSummary(state);
    }

    public synchronized QueuePostingJobSummary update(String id, QueuePostingJobRequest request) {
        JobState state = requireJob(id);
        QueuePostingJobRequest normalized = normalizeQueueRequest(request);
        socialPostingService.requireAccountForJobTargets(normalized.accountId());
        state.accountId = normalized.accountId();
        state.platform = normalized.platform();
        state.intervalHours = normalized.intervalHours();
        state.postsPerRun = normalized.postsPerRun();
        state.minimumReady = normalized.minimumReady();
        state.randomizeUpToHour = Boolean.TRUE.equals(normalized.randomizeUpToHour());
        state.nextRunOffsetMinutes = state.running
                ? normalizeOffset(state.randomizeUpToHour)
                : null;
        persistQueueState(state);

        if (state.running) {
            restartRuntime(state);
        }

        return toSummary(state);
    }

    public synchronized ActionResult delete(String id) {
        JobState state = jobs.remove(id);
        if (state == null) {
            return new ActionResult(false, "delete-posting-job", "Posting job not found.");
        }
        stopRuntime(state.id);
        postingJobRepository.deleteById(id);
        return new ActionResult(true, "delete-posting-job", "Posting job removed.");
    }

    public synchronized ActionResult start(String id) {
        JobState state = requireJob(id);
        if (state.running) {
            return new ActionResult(false, "start-posting-job", "Posting job already running.");
        }
        socialPostingService.requireAccountForJobTargets(state.accountId);

        state.running = true;
        state.startedAt = Instant.now();
        state.lastHeartbeatAt = null;
        state.nextRunOffsetMinutes = normalizeOffset(state.randomizeUpToHour);
        long delay = computeDelayMillis(state.intervalHours, state.randomizeUpToHour);
        state.nextRunAt = Instant.now().plusMillis(delay);
        state.lastRunMessage = "Job scheduled.";
        startRuntime(state, delay);
        persistQueueState(state);
        return new ActionResult(true, "start-posting-job", "Job started.");
    }

    public synchronized ActionResult stop(String id) {
        JobState state = requireJob(id);
        if (!state.running) {
            return new ActionResult(false, "stop-posting-job", "Job is not running.");
        }
        stopRuntime(state.id);
        state.running = false;
        state.lastRunMessage = "Job stopped.";
        state.nextRunAt = null;
        state.nextRunOffsetMinutes = null;
        persistQueueState(state);
        return new ActionResult(true, "stop-posting-job", "Job stopped.");
    }

    public PostingJobStatus status() {
        return legacyStatus();
    }

    public ActionResult start(PostingJobRequest request) {
        return startLegacy(request);
    }

    public ActionResult update(PostingJobRequest request) {
        return updateLegacy(request);
    }

    public ActionResult stop() {
        return stopLegacy();
    }

    public synchronized ActionResult startLegacy(PostingJobRequest request) {
        PostingJobRequest normalized = normalizeLegacyConfig(request);
        legacyState.put("legacy", normalized);
        JobState state = jobs.get("legacy");
        if (state == null) {
            state = new JobState("legacy", "", "all", normalized.intervalHours(), normalized.threadsPerRun(), normalized.minimumReady(), normalized.randomizeUpToHour());
            jobs.put("legacy", state);
        }
        state.running = true;
        state.postsPerRun = normalized.threadsPerRun();
        state.intervalHours = normalized.intervalHours();
        state.minimumReady = normalized.minimumReady();
        state.randomizeUpToHour = Boolean.TRUE.equals(normalized.randomizeUpToHour());
        state.platform = "all";
        state.lastRunMessage = "Legacy posting job started.";
        state.startedAt = Instant.now();
        long delay = computeDelayMillis(state.intervalHours, state.randomizeUpToHour);
        state.nextRunOffsetMinutes = normalizeOffset(state.randomizeUpToHour);
        state.nextRunAt = Instant.now().plusMillis(delay);

        return new ActionResult(true, "start-posting-job",
                "Posting job started. First run is scheduled in " + normalized.intervalHours() + " hour(s)"
                        + (Boolean.TRUE.equals(normalized.randomizeUpToHour()) ? " with up to 1 hour random shift." : "."));
    }

    public synchronized ActionResult updateLegacy(PostingJobRequest request) {
        PostingJobRequest normalized = normalizeLegacyConfig(request);
        legacyState.put("legacy", normalized);
        return new ActionResult(true, "update-posting-job", "Legacy posting job settings updated.");
    }

    public synchronized ActionResult stopLegacy() {
        JobState legacy = jobs.get("legacy");
        if (legacy == null || !legacy.running) {
            return new ActionResult(false, "stop-posting-job", "Posting job is not running.");
        }
        legacy.running = false;
        legacy.lastRunMessage = "Job stopped.";
        return new ActionResult(true, "stop-posting-job", "Posting job stopped.");
    }

    public synchronized PostingJobStatus legacyStatus() {
        JobState legacy = jobs.get("legacy");
        PostingJobRequest config = legacyState.get("legacy");
        PostingJobRequest effective = config == null ? DEFAULT_LEGACY_CONFIG : config;
        if (legacy == null) {
            return toLegacyStatus(effective, false, null, null, null, null, "Job has not run yet.");
        }
        return toLegacyStatus(
                effective,
                legacy.running,
                legacy.startedAt,
                legacy.lastHeartbeatAt,
                legacy.nextRunAt,
                legacy.nextRunOffsetMinutes,
                legacy.lastRunMessage
        );
    }

    private void runSafely(String jobId) {
        JobState state = jobs.get(jobId);
        if (state == null) {
            return;
        }

        synchronized (this) {
            if (!state.running) {
                return;
            }
            state.lastHeartbeatAt = Instant.now();
            state.nextRunAt = null;
            state.nextRunOffsetMinutes = null;
            persistQueueState(state);
        }

        try {
            String result = socialPostingService.publishQueuedForAccountOnce(
                    state.accountId,
                    state.platform,
                    state.postsPerRun,
                    state.minimumReady
            );
            synchronized (this) {
                state.lastHeartbeatAt = Instant.now();
                state.lastRunMessage = result;
            }
        } catch (Exception ex) {
            synchronized (this) {
                state.lastHeartbeatAt = Instant.now();
                state.lastRunMessage = "Job run failed: " + ex.getMessage();
            }
        }

        synchronized (this) {
            if (!state.running) {
                state.nextRunAt = null;
                state.nextRunOffsetMinutes = null;
                persistQueueState(state);
                return;
            }
            long delay = computeDelayMillis(state.intervalHours, state.randomizeUpToHour);
            state.nextRunOffsetMinutes = normalizeOffset(state.randomizeUpToHour);
            state.nextRunAt = Instant.now().plusMillis(delay);
            startRuntime(state, delay);
            persistQueueState(state);
        }
    }

    private synchronized void startRuntime(JobState state, long delayMillis) {
        stopRuntime(state.id);
        if (!state.running) {
            return;
        }
        if (state.id == null) {
            return;
        }
        state.nextRunAt = Instant.now().plusMillis(delayMillis);
        state.nextRunOffsetMinutes = normalizeOffset(state.randomizeUpToHour);
        state.future = executor.schedule(() -> runSafely(state.id), delayMillis, TimeUnit.MILLISECONDS);
        runtimeState.put(state.id, state.future);
    }

    private synchronized void startRuntimeIfConfigured(JobState state) {
        if (!state.running || state.id == null) {
            return;
        }

        long delay = computeDelayMillis(
                state.intervalHours,
                state.randomizeUpToHour
        );
        if (state.nextRunAt != null) {
            delay = Math.max(
                    0,
                    state.nextRunAt.toEpochMilli() - Instant.now().toEpochMilli()
            );
        }
        if (delay < MIN_DELAY_MILLIS) {
            delay = MIN_DELAY_MILLIS;
        }
        startRuntime(state, delay);
    }

    private synchronized void restartRuntime(JobState state) {
        startRuntime(state, computeDelayMillis(state.intervalHours, state.randomizeUpToHour));
    }

    private synchronized void stopRuntime(String jobId) {
        ScheduledFuture<?> scheduled = runtimeState.remove(jobId);
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        JobState state = jobs.get(jobId);
        if (state != null) {
            state.future = null;
        }
    }

    private void persistQueueState(JobState state) {
        if (state == null || state.id == null) {
            return;
        }
        postingJobRepository.save(toEntity(state));
    }

    private QueuePostingJobRequest normalizeQueueRequest(QueuePostingJobRequest request) {
        QueuePostingJobRequest base = request == null ? DEFAULT_JOB_CONFIG : request;
        String accountId = sanitizeText(base.accountId());
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("Queue job accountId is required.");
        }
        String platform = sanitizePlatform(base.platform());
        int intervalHours = base.intervalHours() == null || base.intervalHours() < 1
                ? DEFAULT_JOB_CONFIG.intervalHours()
                : base.intervalHours();
        int postsPerRun = base.postsPerRun() == null || base.postsPerRun() < 1
                ? DEFAULT_JOB_CONFIG.postsPerRun()
                : base.postsPerRun();
        int minimumReady = base.minimumReady() == null || base.minimumReady() < 1
                ? DEFAULT_JOB_CONFIG.minimumReady()
                : base.minimumReady();
        boolean randomize = Boolean.TRUE.equals(base.randomizeUpToHour());
        return new QueuePostingJobRequest(accountId, platform, intervalHours, postsPerRun, minimumReady, randomize);
    }

    private PostingJobRequest normalizeLegacyConfig(PostingJobRequest request) {
        PostingJobRequest current = request == null ? legacyState.get("legacy") : request;
        PostingJobRequest base = current == null ? DEFAULT_LEGACY_CONFIG : current;
        int intervalHours = base.intervalHours() == null || base.intervalHours() < 1
                ? DEFAULT_LEGACY_CONFIG.intervalHours()
                : base.intervalHours();
        int threadsPerRun = base.threadsPerRun() == null || base.threadsPerRun() < 0
                ? DEFAULT_LEGACY_CONFIG.threadsPerRun()
                : base.threadsPerRun();
        int xPerRun = base.xPerRun() == null || base.xPerRun() < 0
                ? DEFAULT_LEGACY_CONFIG.xPerRun()
                : base.xPerRun();
        int minimumReady = base.minimumReady() == null || base.minimumReady() < 1
                ? DEFAULT_LEGACY_CONFIG.minimumReady()
                : base.minimumReady();
        boolean randomizeUpToHour = Boolean.TRUE.equals(base.randomizeUpToHour());
        return new PostingJobRequest(intervalHours, threadsPerRun, xPerRun, minimumReady, randomizeUpToHour);
    }

    private synchronized QueuePostingJobSummary toSummary(JobState state) {
        return new QueuePostingJobSummary(
                state.id,
                state.accountId,
                socialPostingService.resolveAccountLabel(state.accountId),
                state.platform,
                state.running,
                state.intervalHours,
                state.postsPerRun,
                state.minimumReady,
                state.randomizeUpToHour,
                state.startedAt == null ? null : state.startedAt.toString(),
                state.lastHeartbeatAt == null ? null : state.lastHeartbeatAt.toString(),
                state.nextRunAt == null ? null : state.nextRunAt.toString(),
                state.nextRunOffsetMinutes,
                state.lastRunMessage
        );
    }

    private PostingJobStatus toLegacyStatus(
            PostingJobRequest config,
            boolean running,
            Instant startedAt,
            Instant lastHeartbeatAt,
            Instant nextRunAt,
            Integer nextRunOffsetMinutes,
            String lastRunMessage
    ) {
        return new PostingJobStatus(
                running,
                config.intervalHours(),
                config.threadsPerRun(),
                config.xPerRun(),
                config.minimumReady(),
                config.randomizeUpToHour(),
                startedAt == null ? null : startedAt.toString(),
                lastHeartbeatAt == null ? null : lastHeartbeatAt.toString(),
                nextRunAt == null ? null : nextRunAt.toString(),
                nextRunOffsetMinutes,
                lastRunMessage == null ? "Job has not run yet." : lastRunMessage
        );
    }

    private long computeDelayMillis(int intervalHours, boolean randomizeUpToHour) {
        int offsetMinutes = randomizeUpToHour ? ThreadLocalRandom.current().nextInt(-60, 61) : 0;
        long baseDelay = TimeUnit.HOURS.toMillis(Math.max(1, intervalHours));
        long jitterDelay = TimeUnit.MINUTES.toMillis(offsetMinutes);
        long requestedDelay = baseDelay + jitterDelay;
        return Math.max(MIN_DELAY_MILLIS, requestedDelay);
    }

    private Integer normalizeOffset(boolean randomizeUpToHour) {
        if (!randomizeUpToHour) {
            return 0;
        }
        return ThreadLocalRandom.current().nextInt(-60, 61);
    }

    private JobState requireJob(String id) {
        JobState state = jobs.get(sanitizeText(id));
        if (state == null) {
            throw new IllegalStateException("Job not found: " + id);
        }
        return state;
    }

    private String sanitizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String sanitizePlatform(String platform) {
        String normalized = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
        if (!"threads".equals(normalized) && !"x".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
        return normalized;
    }

    private JobState toState(PostingJobEntity entity) {
        JobState state = new JobState(
                entity.getId(),
                entity.getAccountId(),
                entity.getPlatform(),
                entity.getIntervalHours(),
                entity.getPostsPerRun(),
                entity.getMinimumReady(),
                entity.isRandomizeUpToHour()
        );
        state.running = entity.isRunning();
        state.startedAt = entity.getStartedAt();
        state.lastHeartbeatAt = entity.getLastHeartbeatAt();
        state.nextRunAt = entity.getNextRunAt();
        state.nextRunOffsetMinutes = entity.getNextRunOffsetMinutes();
        state.lastRunMessage = entity.getLastRunMessage();
        return state;
    }

    private PostingJobEntity toEntity(JobState state) {
        PostingJobEntity entity = new PostingJobEntity(
                state.id,
                state.accountId,
                state.platform,
                state.intervalHours,
                state.postsPerRun,
                state.minimumReady,
                state.randomizeUpToHour
        );
        entity.setRunning(state.running);
        entity.setStartedAt(state.startedAt);
        entity.setLastHeartbeatAt(state.lastHeartbeatAt);
        entity.setNextRunAt(state.nextRunAt);
        entity.setNextRunOffsetMinutes(state.nextRunOffsetMinutes);
        entity.setLastRunMessage(state.lastRunMessage);
        return entity;
    }

    private static final class JobState {
        private final String id;
        private String accountId;
        private String platform;
        private int intervalHours;
        private int postsPerRun;
        private int minimumReady;
        private boolean randomizeUpToHour;
        private boolean running;
        private Instant startedAt;
        private Instant lastHeartbeatAt;
        private Instant nextRunAt;
        private Integer nextRunOffsetMinutes;
        private String lastRunMessage = "Job initialized.";
        private ScheduledFuture<?> future;

        private JobState(
                String id,
                String accountId,
                String platform,
                int intervalHours,
                int postsPerRun,
                int minimumReady,
                boolean randomizeUpToHour
        ) {
            this.id = id;
            this.accountId = accountId;
            this.platform = platform;
            this.intervalHours = intervalHours;
            this.postsPerRun = postsPerRun;
            this.minimumReady = minimumReady;
            this.randomizeUpToHour = randomizeUpToHour;
        }
    }
}
