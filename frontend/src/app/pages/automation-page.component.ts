import { AsyncPipe, DatePipe, NgClass, NgFor, NgIf } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { interval, map, startWith } from 'rxjs';
import { PostingJobRequest, PostingJobStatus, QueuePost } from '../models/dashboard.models';
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
          <p>Schedule and monitor the recurring posting job here.</p>
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
            <h2>Posting Job</h2>
            <p>Set how often the system should publish automatically.</p>
          </div>
          <div class="job-badge" [ngClass]="{ running: vm.summary.jobStatus?.running, stopped: !vm.summary.jobStatus?.running }">
            {{ vm.summary.jobStatus?.running ? 'Running' : 'Stopped' }}
          </div>
          <div class="form-grid">
            <label>
              <span>Every Hours</span>
              <input [(ngModel)]="jobForm.intervalHours" type="number" min="1" />
            </label>
            <label>
              <span>Threads Per Run</span>
              <input [(ngModel)]="jobForm.threadsPerRun" type="number" min="0" />
            </label>
            <label>
              <span>X Per Run</span>
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
          <p class="feedback" *ngIf="ui.actionResult$ | async as result" [class.error]="!result.success">
            <strong>{{ result.command }}</strong>
            <span>{{ result.message }}</span>
          </p>
        </article>

        <article class="panel">
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
              <dt>Schedule Style</dt>
              <dd>{{ vm.summary.jobStatus?.randomizeUpToHour ? randomizationLabel(vm.summary.jobStatus) : 'Fixed interval without time shift.' }}</dd>
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
        </article>
      </section>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .page { display: grid; gap: 24px; }
    .eyebrow { margin: 0 0 10px; text-transform: uppercase; letter-spacing: 0.14em; font: 700 12px/1.2 "Segoe UI", sans-serif; color: #8a5a24; }
    h1 { margin: 0; font-size: clamp(2.1rem, 4vw, 3.4rem); }
    .page-head p, .panel-head p { color: #52606d; font: 500 16px/1.6 "Segoe UI", sans-serif; margin: 10px 0 0; }
    .timing-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 18px; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
    .timing-card,
    .panel {
      padding: 24px;
      background: rgba(255,255,255,0.78);
      border: 1px solid rgba(31,41,51,0.08);
      border-radius: 24px;
      box-shadow: 0 24px 50px rgba(69,58,42,0.12);
    }
    .timing-card span,
    .panel-head h2 { margin: 0; font-size: 28px; }
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
    .form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 18px; }
    .form-grid label { display: grid; gap: 8px; }
    .form-grid span, .signals dt {
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: #52606d;
      font-family: "Segoe UI", sans-serif;
    }
    .form-grid input {
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
    .toggle-field input {
      width: 18px;
      height: 18px;
      margin-top: 2px;
      accent-color: #1f6feb;
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
    .feedback {
      margin: 18px 0 0;
      padding: 12px 14px;
      border-radius: 14px;
      background: rgba(15,118,110,0.09);
      color: #0f5132;
      font: 600 14px/1.5 "Segoe UI", sans-serif;
      display: grid; gap: 4px;
    }
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
    .preview-body {
      min-width: 0;
      display: grid;
      gap: 6px;
    }
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
    @media (max-width: 900px) {
      .timing-grid, .grid, .form-grid, .actions { grid-template-columns: 1fr; }
      .up-next-card { grid-template-columns: 1fr; }
      .preview-media { width: 100%; aspect-ratio: 16 / 10; }
    }
  `]
})
export class AutomationPageComponent {
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
}
