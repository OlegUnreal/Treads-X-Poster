import { DecimalPipe, NgIf } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChromeProfilesStatus, YoutubePlaybackStatus } from '../models/dashboard.models';
import { DashboardService } from '../services/dashboard.service';

@Component({
  selector: 'app-playback-page',
  standalone: true,
  imports: [DecimalPipe, NgIf, FormsModule],
  template: `
    <section class="page">
      <header class="page-head">
        <div>
          <p class="eyebrow">Playback</p>
          <h1>YouTube Remote</h1>
        </div>
        <span class="mode-pill">{{ percent }}%</span>
      </header>

      <article class="panel">
        <label class="field">
          <span>YouTube URL</span>
          <input class="form-control" [(ngModel)]="url" placeholder="https://www.youtube.com/watch?v=..." />
        </label>

        <div class="slider-row">
          <label class="field">
            <span>Play percent</span>
            <input class="form-range" [(ngModel)]="percent" type="range" min="0" max="100" step="1" />
          </label>
          <input class="form-control form-control-sm percent-input" [(ngModel)]="percent" type="number" min="0" max="100" />
        </div>

        <div class="actions">
          <button class="btn btn-primary btn-sm" type="button" [disabled]="busy" (click)="play()">
            {{ busy ? 'Opening...' : 'Open and play' }}
          </button>
          <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="busy" (click)="stop()">Stop</button>
          <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="busy" (click)="refreshStatus()">Refresh</button>
          <a class="btn btn-outline-secondary btn-sm" href="/api/actions/youtube/screenshot" target="_blank" rel="noopener">Screenshot</a>
        </div>

        <div class="profiles-actions">
          <button class="btn btn-outline-primary btn-sm" type="button" [disabled]="profilesBusy" (click)="startProfiles()">
            {{ profilesBusy ? 'Starting...' : 'Start proxy Chrome profiles' }}
          </button>
          <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="profilesBusy" (click)="refreshProfilesStatus()">Profiles status</button>
        </div>

        <p class="feedback" *ngIf="message" [class.error]="error">{{ message }}</p>
        <p class="feedback" *ngIf="profilesMessage" [class.error]="profilesError">{{ profilesMessage }}</p>

        <dl class="status" *ngIf="status">
          <div>
            <dt>Status</dt>
            <dd>{{ playbackLabel(status) }}</dd>
          </div>
          <div>
            <dt>Target</dt>
            <dd>{{ status.percent ?? percent }}%</dd>
          </div>
          <div>
            <dt>Video</dt>
            <dd>{{ status.videoPresent ? 'Detected' : 'Not found' }}</dd>
          </div>
          <div *ngIf="status.currentTime !== undefined">
            <dt>Time</dt>
            <dd>{{ status.currentTime | number:'1.0-0' }} sec</dd>
          </div>
          <div *ngIf="status.durationSeconds">
            <dt>Duration</dt>
            <dd>{{ status.durationSeconds | number:'1.0-0' }} sec</dd>
          </div>
          <div *ngIf="status.browser">
            <dt>Browser</dt>
            <dd>{{ status.browser }}</dd>
          </div>
        </dl>

        <pre class="log-tail" *ngIf="profilesStatus?.logTail">{{ profilesStatus?.logTail }}</pre>
      </article>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .page { display: grid; gap: 12px; }
    .page-head { display: flex; justify-content: space-between; align-items: end; gap: 12px; }
    .page-head h1 { margin: 0; font-size: 28px; line-height: 1.05; }
    .eyebrow, .field span, .status dt {
      margin: 0 0 4px;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: #64748b;
      font: 700 11px/1.2 "Segoe UI", sans-serif;
    }
    .mode-pill {
      padding: 6px 10px;
      border-radius: 999px;
      background: #e7f1ff;
      color: #0b5ed7;
      font: 800 12px/1 "Segoe UI", sans-serif;
    }
    .panel {
      max-width: 860px;
      padding: 14px;
      background: #fff;
      border: 1px solid #dde3ea;
      border-radius: 12px;
      box-shadow: 0 8px 22px rgba(15, 23, 42, 0.05);
    }
    .field { display: grid; gap: 4px; }
    .slider-row { display: grid; grid-template-columns: minmax(0, 1fr) 92px; gap: 10px; align-items: end; margin-top: 12px; }
    .percent-input { text-align: center; }
    .actions { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; margin-top: 12px; }
    .profiles-actions { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; margin-top: 8px; padding-top: 8px; border-top: 1px dashed #dde3ea; }
    .feedback {
      margin: 10px 0 0;
      padding: 8px 10px;
      border-radius: 8px;
      background: #e9f7ef;
      color: #146c43;
      font: 700 13px/1.35 "Segoe UI", sans-serif;
    }
    .feedback.error { background: #fff1f2; color: #be123c; }
    .status { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin: 12px 0 0; }
    .status div { padding: 10px; border: 1px solid #dde3ea; border-radius: 10px; background: #f8fafc; }
    .status dd { margin: 0; color: #17212b; font: 800 14px/1.3 "Segoe UI", sans-serif; overflow-wrap: anywhere; }
    .log-tail { margin: 12px 0 0; max-height: 220px; overflow: auto; padding: 10px; border-radius: 10px; background: #0f172a; color: #dbeafe; font: 600 12px/1.45 Consolas, monospace; white-space: pre-wrap; }
    @media (max-width: 760px) { .page-head, .slider-row, .status { grid-template-columns: 1fr; display: grid; } .actions button { width: 100%; } }
  `]
})
export class PlaybackPageComponent {
  private readonly dashboardService = inject(DashboardService);

  protected url = '';
  protected percent = 100;
  protected busy = false;
  protected message = '';
  protected error = false;
  protected status: YoutubePlaybackStatus | null = null;
  protected profilesBusy = false;
  protected profilesMessage = '';
  protected profilesError = false;
  protected profilesStatus: ChromeProfilesStatus | null = null;

  protected play(): void {
    const cleanUrl = this.url.trim();
    if (!cleanUrl) {
      this.message = 'Add a YouTube URL first.';
      this.error = true;
      return;
    }
    this.busy = true;
    this.error = false;
    this.message = 'Opening YouTube on the server...';
    this.dashboardService.playYoutube({ url: cleanUrl, percent: this.normalizedPercent() }).subscribe({
      next: (status) => {
        this.status = status;
        this.message = `Playback started. Target: ${status.percent ?? this.percent}%.`;
        this.error = false;
        this.busy = false;
      },
      error: (error) => {
        this.message = error?.error?.message || error?.message || 'Could not start YouTube playback.';
        this.error = true;
        this.busy = false;
      }
    });
  }

  protected stop(): void {
    this.busy = true;
    this.dashboardService.stopYoutube().subscribe({
      next: (status) => {
        this.status = status;
        this.message = 'Playback stopped.';
        this.error = false;
        this.busy = false;
      },
      error: (error) => {
        this.message = error?.error?.message || error?.message || 'Could not stop playback.';
        this.error = true;
        this.busy = false;
      }
    });
  }

  protected refreshStatus(): void {
    this.dashboardService.getYoutubeStatus().subscribe((status) => {
      this.status = status;
      this.message = 'Status refreshed.';
      this.error = false;
    });
  }

  protected startProfiles(): void {
    this.profilesBusy = true;
    this.profilesError = false;
    this.profilesMessage = 'Starting proxy Chrome profiles...';
    this.dashboardService.startAllChromeProfiles().subscribe({
      next: (status) => {
        this.profilesStatus = status;
        this.profilesMessage = status.message || 'Chrome proxy profiles started.';
        this.profilesError = false;
        this.profilesBusy = false;
      },
      error: (error) => {
        this.profilesMessage = error?.error?.message || error?.message || 'Could not start proxy Chrome profiles.';
        this.profilesError = true;
        this.profilesBusy = false;
      }
    });
  }

  protected refreshProfilesStatus(): void {
    this.profilesBusy = true;
    this.dashboardService.getChromeProfilesStatus().subscribe({
      next: (status) => {
        this.profilesStatus = status;
        this.profilesMessage = status.scriptExists ? 'Profile launcher is available.' : 'Profile launcher script is missing.';
        this.profilesError = !status.scriptExists;
        this.profilesBusy = false;
      },
      error: (error) => {
        this.profilesMessage = error?.error?.message || error?.message || 'Could not read profile launcher status.';
        this.profilesError = true;
        this.profilesBusy = false;
      }
    });
  }

  protected playbackLabel(status: YoutubePlaybackStatus): string {
    if (status.status === 'idle') {
      return 'idle';
    }
    if (!status.videoPresent) {
      return status.status;
    }
    return status.paused ? 'paused' : 'playing';
  }

  private normalizedPercent(): number {
    const numeric = Number(this.percent);
    if (!Number.isFinite(numeric)) {
      return 100;
    }
    return Math.max(0, Math.min(100, Math.round(numeric)));
  }
}
