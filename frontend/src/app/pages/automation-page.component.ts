import { AsyncPipe, DatePipe, NgClass, NgFor, NgIf } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { interval, map, startWith } from 'rxjs';
import {
  PostingJobRequest,
  PostingJobStatus,
  QueuePost,
  QueuePostingJob,
  QueuePostingJobRequest,
  PublisherAccountSummary
} from '../models/dashboard.models';
import { DashboardService } from '../services/dashboard.service';
import { AdminUiStateService } from '../services/admin-ui-state.service';

@Component({
  selector: 'app-automation-page',
  standalone: true,
  imports: [AsyncPipe, DatePipe, NgClass, NgFor, NgIf, FormsModule],
  template: `
    <section class="page" *ngIf="ui.vm$ | async as vm">
      <ng-container *ngIf="syncJobFormFromStatus(vm.summary.jobStatus)"></ng-container>
      <header class="page-head">
        <div>
          <p class="eyebrow">Automation</p>
          <h1>Automation</h1>
          <p>Schedule automatic generation and publishing across connected accounts.</p>
        </div>
      </header>

      <section class="timing-grid" *ngIf="now$ | async as now">
        <article class="timing-card">
          <span>Current Time</span>
          <strong>{{ now | date:'yyyy-MM-dd HH:mm:ss' }}</strong>
        </article>
        <article class="timing-card">
          <span>Next Run</span>
          <strong>{{ nextRunAt(vm.summary.jobStatus) ? (nextRunAt(vm.summary.jobStatus) | date:'yyyy-MM-dd HH:mm:ss') : 'Not scheduled' }}</strong>
        </article>
        <article class="timing-card">
          <span>Time Remaining</span>
          <strong>{{ countdownLabel(vm.summary.jobStatus, now) }}</strong>
        </article>
        <article class="timing-card">
          <span>Timing Mode</span>
          <strong>{{ vm.summary.jobStatus?.randomizeUpToHour ? randomizationLabel(vm.summary.jobStatus) : 'Fixed schedule' }}</strong>
        </article>
      </section>

      <section class="grid">
        <article class="panel">
          <div class="panel-head">
            <h2>Legacy account automation</h2>
            <p>The legacy job checks every configured account and publishes from each ready queue.</p>
          </div>
          <div class="job-badge" [ngClass="{ running: vm.summary.jobStatus?.running, stopped: !vm.summary.jobStatus?.running }">
            {{ vm.summary.jobStatus?.running ? 'Running' : 'Stopped' }}
          </div>
          <div class="form-grid">
            <label>
              <span>Every Hours</span>
              <input [(ngModel)]="jobForm.intervalHours" type="number" min="1" />
            </label>
            <label>
              <span>Threads Per Account</span>
              <input [(ngModel)]="jobForm.threadsPerRun" type="number" min="0" />
            </label>
            <label>
              <span>X Per Account</span>
              <input [(ngModel)]="jobForm.xPerRun" type="number" min="0" />
            </label>
            <label>
              <span>Minimum Ready</span>
              <input [(ngModel)]="jobForm.minimumReady" type="number" min="1" />
            </label>
            <label class="toggle-field wide">
              <input [(ngModel)]="jobForm.randomizeUpToHour" type="checkbox" />
              <div>
                <span>Allow up to 1 hour shift</span>
                <small>Each automatic publishing run may happen up to 1 hour earlier or later.</small>
              </div>
            </label>
          </div>
          <div class="actions">
            <button type="button" (click)="startPostingJob()">Start Job</button>
            <button type="button" class="ghost" (click)="updatePostingJob()">Update Job</button>
            <button type="button" class="secondary" (click)="stopPostingJob()">Stop Job</button>
          </div>
        </article>

        <article class="panel">
          <div class="panel-head">
            <h2>Queue Jobs</h2>
            <p>Every queue job is bound to one account and one profile (X or Threads).</p>
          </div>

          <form class="queue-job-form" (submit)="$event.preventDefault(); submitQueueJob(vm.summary.publisherAccounts.activeAccountId)">
            <label>
              <span>Account</span>
              <select [(ngModel)]="queueJobForm.accountId" name="queueJobAccount">
                <option *ngFor="let account of vm.summary.publisherAccounts.availableAccounts" [value]="account.id">{{ account.label }}</option>
              </select>
            </label>
            <label>
              <span>Profile</span>
              <select [(ngModel)]="queueJobForm.platform" name="queueJobPlatform">
                <option value="x">X</option>
                <option value="threads">Threads</option>
              </select>
            </label>
            <label>
              <span>Every Hours</span>
              <input [(ngModel)]="queueJobForm.intervalHours" type="number" min="1" name="queueJobInterval" />
            </label>
            <label>
              <span>Posts per Run</span>
              <input [(ngModel)]="queueJobForm.postsPerRun" type="number" min="1" name="queueJobPosts" />
            </label>
            <label>
              <span>Minimum Ready</span>
              <input [(ngModel)]="queueJobForm.minimumReady" type="number" min="1" name="queueJobMinimum" />
            </label>
            <label class="toggle-field inline-toggle">
              <input [(ngModel)]="queueJobForm.randomizeUpToHour" type="checkbox" name="queueJobRandomize" />
              <div>
                <span>Allow up to 1 hour shift</span>
                <small>Individual runs can be jittered.</small>
              </div>
            </label>
            <div class="row-actions">
              <button type="submit">{{ editingQueueJobId ? 'Update job' : 'Create job' }}</button>
              <button type="button" class="ghost" (click)="resetQueueJobForm()">Reset</button>
            </div>
          </form>

          <div class="job-list" *ngIf="queueJobs.length > 0; else noJobs">
            <article class="queue-job-card" *ngFor="let job of queueJobs">
              <div>
                <strong>{{ job.accountLabel }} · {{ queueJobProfileLabel(vm.summary.publisherAccounts, job) }}</strong>
                <span class="muted">{{ job.intervalHours }}h · {{ job.postsPerRun }} post(s) · min ready {{ job.minimumReady }}</span>
              </div>
              <div class="status-line">
                <span class="job-state" [class.on]="job.running">{{ job.running ? 'Running' : 'Stopped' }}</span>
                <span *ngIf="job.lastRunMessage">{{ job.lastRunMessage }}</span>
              </div>
              <div class="job-actions">
                <button type="button" (click)="editQueueJob(job)">Edit</button>
                <button type="button" class="ghost" (click)="job.running ? stopQueueJob(job.id) : startQueueJob(job.id)">{{ job.running ? 'Stop' : 'Start' }}</button>
                <button type="button" class="danger" (click)="removeQueueJob(job.id)">Delete</button>
              </div>
            </article>
          </div>
          <ng-template #noJobs>
            <p class="empty-copy">No queue jobs yet. Create one to run a profile-specific queue automatically.</p>
          </ng-template>
        </article>
      </section>

      <section class="panel signals-wrap">
        <div class="panel-head">
          <h2>Automation Signals</h2>
          <p>Recent status, next run timing, and the posts most likely to be published next.</p>
        </div>
        <dl class="signals">
          <div>
            <dt>Started</dt>
            <dd>{{ vm.summary.jobStatus?.startedAt ? (vm.summary.jobStatus?.startedAt | date:'yyyy-MM-dd HH:mm:ss') : 'Not started yet' }}</dd>
          </div>
          <div>
            <dt>Last Heartbeat</dt>
            <dd>{{ vm.summary.jobStatus?.lastHeartbeatAt ? (vm.summary.jobStatus?.lastHeartbeatAt | date:'yyyy-MM-dd HH:mm:ss') : 'No heartbeat yet' }}</dd>
          </div>
          <div>
            <dt>Last Job Message</dt>
            <dd>{{ vm.summary.jobStatus?.lastRunMessage || 'Job has not run yet.' }}</dd>
          </div>
          <div>
            <dt>Runtime Signals</dt>
            <dd>{{ vm.summary.lastDailyMessage }}</dd>
          </div>
          <div>
            <dt>Publisher</dt>
            <dd>{{ vm.summary.lastThreadsMessage }}</dd>
          </div>
        </dl>

        <div class="up-next">
          <h3>Up Next For Threads</h3>
          <div class="up-next-list" *ngIf="nextThreadsPosts(vm.queue).length > 0; else noThreads">
            <article class="up-next-card" *ngFor="let post of nextThreadsPosts(vm.queue)">
              <div class="preview-media" *ngIf="post.imageUrl">
                <img [src]="post.imageUrl" [alt]="post.topic || 'Threads preview image'" />
              </div>
              <div class="preview-body">
                <strong>{{ post.topic || 'Untitled post' }}</strong>
                <p>{{ post.text }}</p>
                <span class="preview-meta" *ngIf="post.imageUrl">Photo attached</span>
              </div>
            </article>
          </div>
          <ng-template #noThreads>
            <p class="empty-copy">No ready Threads posts are waiting right now.</p>
          </ng-template>
        </div>

        <div class="up-next">
          <h3>Up Next For X</h3>
          <div class="up-next-list" *ngIf="nextXPosts(vm.queue).length > 0; else noX">
            <article class="up-next-card" *ngFor="let post of nextXPosts(vm.queue)">
              <div class="preview-media" *ngIf="post.imageUrl">
                <img [src]="post.imageUrl" [alt]="post.topic || 'X preview image'" />
              </div>
              <div class="preview-body">
                <strong>{{ post.topic || 'Untitled post' }}</strong>
                <p>{{ post.text }}</p>
                <span class="preview-meta" *ngIf="post.imageUrl">Photo attached</span>
              </div>
            </article>
          </div>
          <ng-template #noX>
            <p class="empty-copy">No ready X posts are waiting right now.</p>
          </ng-template>
        </div>
      </section>

      <p class="feedback" *ngIf="ui.actionResult$ | async as result" [class.error]="!result.success">
        <strong>{{ result.command }}</strong>
        <span>{{ result.message }}</span>
      </p>
    </section>
  `,
  styles: [
    `
    :host { display: block; }
    .page { display: grid; gap: 24px; }
    .eyebrow { margin: 0 0 10px; text-transform: uppercase; letter-spacing: 0.14em; font: 700 12px/1.2 "Segoe UI", sans-serif; color: #8a5a24; }
    h1 { margin: 0; font-size: clamp(2.1rem, 4vw, 3.4rem); }
    .page-head p, .panel-head p { color: #52606d; font: 500 16px/1.6 "Segoe UI", sans-serif; margin: 10px 0 0; }
    .timing-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 18px; }
    .grid { display: grid; grid-template-columns: 2fr 3fr; gap: 18px; }
    .timing-card,
    .panel {
      padding: 24px;
      background: rgba(255,255,255,0.78);
      border: 1px solid rgba(31,41,51,0.08);
      border-radius: 24px;
      box-shadow: 0 24px 50px rgba(69,58,42,0.12);
    }
    .timing-card span,
    .panel-head h2 {
      margin: 0;
      font-size: 28px;
    }
    .timing-card span {
      display: block;
      color: #52606d;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      margin-bottom: 10px;
      font-family: "Segoe UI", sans-serif;
    }
    .timing-card strong {
      display: block;
      color: #243b53;
      font-size: 26px;
      line-height: 1.3;
    }
    .job-badge {
      display: inline-flex;
      align-items: center;
      margin-top: 18px;
      padding: 8px 12px;
      border-radius: 999px;
      font: 700 12px/1 "Segoe UI", sans-serif;
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }
    .job-badge.running { background: rgba(15,118,110,0.12); color: #0f766e; }
    .job-badge.stopped { background: rgba(154,52,18,0.12); color: #9a3412; }
    .form-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 14px;
      margin-top: 18px;
    }
    .form-grid label { display: grid; gap: 8px; }
    .form-grid span,
    .signals dt,
    .queue-job-form label > span {
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: #52606d;
      font-family: "Segoe UI", sans-serif;
    }
    .form-grid input,
    .queue-job-form input,
    .queue-job-form select {
      width: 100%;
      border: 1px solid rgba(31,41,51,0.12);
      border-radius: 14px;
      padding: 12px 14px;
      background: rgba(255,255,255,0.92);
      color: #243b53;
      font: 500 14px/1.5 "Segoe UI", sans-serif;
    }
    .toggle-field {
      grid-template-columns: auto minmax(0, 1fr);
      align-items: start;
      gap: 12px;
      padding: 14px 16px;
      border-radius: 16px;
      border: 1px solid rgba(31,41,51,0.08);
      background: rgba(255,255,255,0.78);
    }
    .toggle-field input,
    .inline-toggle input { margin-top: 2px; }
    .inline-toggle {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      align-items: center;
      gap: 12px;
      margin-top: 6px;
      padding: 12px;
      border: 1px solid rgba(31,41,51,0.08);
      border-radius: 14px;
      background: rgba(255,255,255,0.78);
    }
    .toggle-field small {
      color: #52606d;
      font: 500 13px/1.5 "Segoe UI", sans-serif;
    }
    .actions { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 18px; }
    button {
      border: 0;
      border-radius: 18px;
      padding: 14px 16px;
      font: 700 14px/1.2 "Segoe UI", sans-serif;
      background: linear-gradient(135deg, #1f6feb, #0f766e);
      color: white;
      cursor: pointer;
      box-shadow: 0 16px 30px rgba(21, 48, 74, 0.2);
    }
    button.ghost {
      background: rgba(255,255,255,0.92);
      color: #243b53;
      border: 1px solid rgba(31,41,51,0.12);
      box-shadow: none;
    }
    button.secondary { background: linear-gradient(135deg, #8a5a24, #9a3412); }
    button.danger { background: linear-gradient(135deg, #b91c1c, #7f1d1d); }
    .feedback { padding: 12px 14px; border-radius: 14px; background: rgba(15,118,110,0.09); color: #0f5132; font: 600 14px/1.5 "Segoe UI", sans-serif; }
    .feedback.error { background: rgba(154,52,18,0.09); color: #7c2d12; }
    .signals { margin: 18px 0 0; display: grid; gap: 16px; font-family: "Segoe UI", sans-serif; }
    .signals dd { margin: 6px 0 0; color: #334e68; line-height: 1.6; }
    .up-next { margin-top: 24px; }
    .up-next h3 {
      margin: 0 0 12px;
      font-size: 18px;
      color: #1f2933;
      font-family: Georgia, "Times New Roman", serif;
    }
    .up-next-list { display: grid; gap: 12px; }
    .up-next-card {
      padding: 14px 16px;
      border-radius: 16px;
      background: rgba(255,255,255,0.7);
      border: 1px solid rgba(31,41,51,0.08);
      display: grid;
      grid-template-columns: 120px minmax(0, 1fr);
      gap: 14px;
      align-items: start;
    }
    .preview-media {
      width: 120px;
      aspect-ratio: 1 / 1;
      border-radius: 14px;
      overflow: hidden;
      background: rgba(31,41,51,0.06);
      border: 1px solid rgba(31,41,51,0.08);
    }
    .preview-media img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
    .preview-body { min-width: 0; display: grid; gap: 6px; }
    .up-next-card strong {
      display: block;
      color: #243b53;
      font: 700 15px/1.4 "Segoe UI", sans-serif;
    }
    .up-next-card p,
    .empty-copy {
      margin: 0;
      color: #52606d;
      font: 500 14px/1.6 "Segoe UI", sans-serif;
    }
    .preview-meta {
      color: #8a5a24;
      font: 700 12px/1.4 "Segoe UI", sans-serif;
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }
    .queue-job-form {
      display: grid;
      gap: 10px;
      margin-top: 12px;
      margin-bottom: 14px;
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
    .queue-job-form label { display: grid; gap: 6px; }
    .queue-job-form .row-actions {
      grid-column: 1 / -1;
      display: grid;
      gap: 8px;
      grid-template-columns: 1fr 1fr;
    }
    .job-list { margin-top: 10px; display: grid; gap: 10px; }
    .queue-job-card {
      display: grid;
      gap: 8px;
      padding: 12px;
      border-radius: 14px;
      border: 1px solid rgba(31,41,51,0.08);
      background: rgba(255,255,255,0.66);
    }
    .queue-job-card .muted,
    .status-line span { color: #64748b; font: 500 13px/1.5 "Segoe UI", sans-serif; }
    .status-line {
      display: grid;
      gap: 6px;
    }
    .job-actions {
      display: grid;
      gap: 6px;
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
    .job-state {
      display: inline-flex;
      padding: 5px 10px;
      border-radius: 999px;
      width: fit-content;
      background: rgba(148,163,184,0.2);
      color: #334155;
      font: 700 11px/1 "Segoe UI", sans-serif;
      text-transform: uppercase;
    }
    .job-state.on {
      background: rgba(5,150,105,0.16);
      color: #047857;
    }
    .signals-wrap { margin-top: -6px; }
    @media (max-width: 1200px) { .grid, .timing-grid, .form-grid, .queue-job-form { grid-template-columns: 1fr; } }
    @media (max-width: 900px) {
      .up-next-card { grid-template-columns: 1fr; }
      .preview-media { width: 100%; aspect-ratio: 16 / 10; }
      .job-actions { grid-template-columns: 1fr; }
    }
  `
  ]
})
export class AutomationPageComponent implements OnInit {
  protected readonly ui = inject(AdminUiStateService);
  private readonly dashboardService = inject(DashboardService);
  protected readonly now$ = interval(1000).pipe(
    startWith(0),
    map(() => new Date())
  );
  private jobFormSynced = false;

  protected jobForm: PostingJobRequest = {
    intervalHours: 4,
    threadsPerRun: 1,
    xPerRun: 1,
    minimumReady: 8,
    randomizeUpToHour: false
  };

  protected queueJobs: QueuePostingJob[] = [];
  protected editingQueueJobId: string | null = null;
  protected queueJobForm: QueuePostingJobRequest = {
    accountId: '',
    platform: 'x',
    intervalHours: 4,
    postsPerRun: 1,
    minimumReady: 8,
    randomizeUpToHour: false
  };

  public ngOnInit(): void {
    this.refreshQueueJobs();
  }

  protected startPostingJob(): void {
    this.dashboardService.startJob(this.jobForm).subscribe((result) => {
      if (result.success) {
        this.jobFormSynced = false;
      }
      this.ui.pushActionResult(result);
    });
  }

  protected updatePostingJob(): void {
    this.dashboardService.updateJob(this.jobForm).subscribe((result) => {
      if (result.success) {
        this.jobFormSynced = false;
      }
      this.ui.pushActionResult(result);
    });
  }

  protected stopPostingJob(): void {
    this.dashboardService.stopJob().subscribe((result) => this.ui.pushActionResult(result));
  }

  protected syncJobFormFromStatus(jobStatus: PostingJobStatus | null): boolean {
    if (this.jobFormSynced || !jobStatus) {
      return true;
    }

    this.jobForm = {
      intervalHours: jobStatus.intervalHours ?? 4,
      threadsPerRun: jobStatus.threadsPerRun ?? 1,
      xPerRun: jobStatus.xPerRun ?? 1,
      minimumReady: jobStatus.minimumReady ?? 8,
      randomizeUpToHour: jobStatus.randomizeUpToHour ?? false
    };
    this.jobFormSynced = true;
    return true;
  }

  protected resetQueueJobForm(): void {
    if (this.queueJobs.length > 0) {
      this.queueJobForm.accountId = this.queueJobs[0].accountId;
    } else {
      this.queueJobForm.accountId = '';
    }
    this.queueJobForm.platform = 'x';
    this.queueJobForm.intervalHours = 4;
    this.queueJobForm.postsPerRun = 1;
    this.queueJobForm.minimumReady = 8;
    this.queueJobForm.randomizeUpToHour = false;
    this.editingQueueJobId = null;
  }

  protected submitQueueJob(activeAccountId: string): void {
    const sanitized: QueuePostingJobRequest = {
      accountId: this.queueJobForm.accountId || activeAccountId,
      platform: this.queueJobForm.platform || 'x',
      intervalHours: Math.max(1, Number(this.queueJobForm.intervalHours) || 1),
      postsPerRun: Math.max(1, Number(this.queueJobForm.postsPerRun) || 1),
      minimumReady: Math.max(1, Number(this.queueJobForm.minimumReady) || 8),
      randomizeUpToHour: !!this.queueJobForm.randomizeUpToHour
    };

    const request = sanitized;
    const action = this.editingQueueJobId
      ? this.dashboardService.updateQueueJob(this.editingQueueJobId, request)
      : this.dashboardService.createQueueJob(request);

    action.subscribe((result: QueuePostingJob | { success: boolean; command: string; message: string }) => {
      if ('id' in result) {
        this.refreshQueueJobs();
        this.resetQueueJobForm();
        this.ui.pushActionResult({
          success: true,
          command: this.editingQueueJobId ? 'update-queue-job' : 'create-queue-job',
          message: this.editingQueueJobId ? `Updated queue job for ${result.accountLabel}.` : 'Queue job created.'
        });
      } else {
        this.ui.pushActionResult(result);
      }
    });
  }

  protected editQueueJob(job: QueuePostingJob): void {
    this.queueJobForm = {
      accountId: job.accountId,
      platform: job.platform,
      intervalHours: job.intervalHours,
      postsPerRun: job.postsPerRun,
      minimumReady: job.minimumReady,
      randomizeUpToHour: job.randomizeUpToHour
    };
    this.editingQueueJobId = job.id;
  }

  protected startQueueJob(jobId: string): void {
    this.dashboardService.startQueueJob(jobId).subscribe((result) => {
      this.ui.pushActionResult(result);
      if (result.success) {
        this.refreshQueueJobs();
      }
    });
  }

  protected stopQueueJob(jobId: string): void {
    this.dashboardService.stopQueueJob(jobId).subscribe((result) => {
      this.ui.pushActionResult(result);
      if (result.success) {
        this.refreshQueueJobs();
      }
    });
  }

  protected removeQueueJob(jobId: string): void {
    if (!window.confirm('Delete this queue job?')) {
      return;
    }
    this.dashboardService.deleteQueueJob(jobId).subscribe((result) => {
      this.ui.pushActionResult(result);
      this.refreshQueueJobs();
    });
  }

  private refreshQueueJobs(): void {
    this.dashboardService.getQueueJobs().subscribe((jobs) => {
      this.queueJobs = jobs;
      if (!this.queueJobForm.accountId && this.queueJobs.length > 0) {
        this.queueJobForm.accountId = this.queueJobs[0].accountId;
      }
    });
  }

  protected nextRunAt(jobStatus: PostingJobStatus | null): Date | null {
    if (!jobStatus?.running) {
      return null;
    }

    if (jobStatus.nextRunAt) {
      return new Date(jobStatus.nextRunAt);
    }

    if (!jobStatus.intervalHours) {
      return null;
    }

    const baseTime = jobStatus.lastHeartbeatAt ?? jobStatus.startedAt;
    if (!baseTime) {
      return null;
    }

    const next = new Date(baseTime);
    next.setHours(next.getHours() + jobStatus.intervalHours);
    return next;
  }

  protected countdownLabel(jobStatus: PostingJobStatus | null, now: Date): string {
    const next = this.nextRunAt(jobStatus);
    if (!next) {
      return 'Not running';
    }

    const diffMs = next.getTime() - now.getTime();
    if (diffMs <= 0) {
      return 'Any moment now';
    }

    const totalSeconds = Math.floor(diffMs / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    return `${hours}h ${minutes}m ${seconds}s`;
  }

  protected randomizationLabel(jobStatus: PostingJobStatus | null): string {
    if (!jobStatus?.randomizeUpToHour) {
      return 'Fixed schedule';
    }

    const offset = jobStatus.nextRunOffsetMinutes;
    if (offset == null) {
      return 'Flexible schedule (+/- 1h)';
    }

    if (offset === 0) {
      return 'Flexible schedule (no shift this time)';
    }

    const direction = offset > 0 ? 'later' : 'earlier';
    return `Flexible schedule (${Math.abs(offset)} min ${direction})`;
  }

  protected nextThreadsPosts(queue: QueuePost[]) {
    return queue
      .filter((post) => post.status === 'ready' && (post.platforms ?? []).includes('threads') && !post.published?.['threads'])
      .slice(0, Math.max(this.jobForm.threadsPerRun, 1));
  }

  protected nextXPosts(queue: QueuePost[]) {
    return queue
      .filter((post) => post.status === 'ready' && (post.platforms ?? []).includes('x') && !post.published?.['x'])
      .slice(0, Math.max(this.jobForm.xPerRun, 1));
  }

  protected queueJobProfileLabel(summary: PublisherAccountSummary, job: QueuePostingJob): string {
    const profile = this.ui
      .publishingProfiles(summary)
      .find((profileOption) => profileOption.accountId === job.accountId && profileOption.platform === job.platform);
    return profile?.name || job.platform.toUpperCase();
  }
}
