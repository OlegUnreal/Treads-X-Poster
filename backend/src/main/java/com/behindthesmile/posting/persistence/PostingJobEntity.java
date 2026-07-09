package com.behindthesmile.posting.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "posting_jobs")
public class PostingJobEntity {
    @Id
    @Column(name = "job_id", length = 64, nullable = false)
    private String id;

    @Column(name = "account_id", length = 128, nullable = false)
    private String accountId;

    @Column(name = "platform", length = 16, nullable = false)
    private String platform;

    @Column(name = "interval_hours", nullable = false)
    private int intervalHours;

    @Column(name = "posts_per_run", nullable = false)
    private int postsPerRun;

    @Column(name = "minimum_ready", nullable = false)
    private int minimumReady;

    @Column(name = "randomize_up_to_hour", nullable = false)
    private boolean randomizeUpToHour;

    @Column(name = "running", nullable = false)
    private boolean running;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "next_run_offset_minutes")
    private Integer nextRunOffsetMinutes;

    @Column(name = "last_run_message", length = 2000)
    private String lastRunMessage = "Job initialized.";

    protected PostingJobEntity() {
    }

    public PostingJobEntity(
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public int getIntervalHours() {
        return intervalHours;
    }

    public void setIntervalHours(int intervalHours) {
        this.intervalHours = intervalHours;
    }

    public int getPostsPerRun() {
        return postsPerRun;
    }

    public void setPostsPerRun(int postsPerRun) {
        this.postsPerRun = postsPerRun;
    }

    public int getMinimumReady() {
        return minimumReady;
    }

    public void setMinimumReady(int minimumReady) {
        this.minimumReady = minimumReady;
    }

    public boolean isRandomizeUpToHour() {
        return randomizeUpToHour;
    }

    public void setRandomizeUpToHour(boolean randomizeUpToHour) {
        this.randomizeUpToHour = randomizeUpToHour;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public Integer getNextRunOffsetMinutes() {
        return nextRunOffsetMinutes;
    }

    public void setNextRunOffsetMinutes(Integer nextRunOffsetMinutes) {
        this.nextRunOffsetMinutes = nextRunOffsetMinutes;
    }

    public String getLastRunMessage() {
        return lastRunMessage;
    }

    public void setLastRunMessage(String lastRunMessage) {
        this.lastRunMessage = lastRunMessage;
    }
}

