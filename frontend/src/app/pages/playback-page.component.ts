import { DecimalPipe, NgFor, NgIf } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChromeProfilesStatus, ChromeProfilesUrlCheckStatus, DesktopUpdateStatus, YoutubePlaybackStatus } from '../models/dashboard.models';
import { DashboardService } from '../services/dashboard.service';

@Component({
  selector: 'app-playback-page',
  standalone: true,
  imports: [DecimalPipe, NgFor, NgIf, FormsModule],
  template: `
    <section class="page">
      <header class="page-head">
        <div>
          <p class="eyebrow">Playback</p>
          <h1>Profiles</h1>
        </div>
        <span class="mode-pill">{{ isYoutubeUrl() ? 'YouTube' : 'Website' }}</span>
      </header>

      <article class="panel">
        <label class="field">
          <span>Video or website URL</span>
          <input class="form-control" [(ngModel)]="url" placeholder="https://example.com/video-page" />
        </label>

        <div class="slider-row" *ngIf="isYoutubeUrl()">
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
          <button class="btn btn-outline-primary btn-sm" type="button" [disabled]="checkBusy" (click)="checkUrl()">
            {{ checkBusy ? 'Checking...' : 'Check URL' }}
          </button>
          <button class="btn btn-outline-success btn-sm" type="button" [disabled]="busy || !checkedProfileNames().length" (click)="openCheckedProfiles()">
            Open checked
          </button>
          <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="busy" (click)="stop()">Stop</button>
          <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="busy" (click)="refreshStatus()">Refresh</button>
          <a class="btn btn-outline-secondary btn-sm" href="/api/actions/youtube/screenshot" target="_blank" rel="noopener">Screenshot</a>
          <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="updateBusy" (click)="checkForUpdates()">
            {{ updateBusy ? 'Checking update...' : 'Check update' }}
          </button>
        </div>

        <div class="desktop-status" *ngIf="profilesStatus">
          <div [class.ok]="profilesStatus.chromeFound" [class.bad]="profilesStatus.chromeFound === false">
            <strong>Chrome</strong>
            <span>{{ profilesStatus.chromeFound ? 'Found' : 'Missing' }}</span>
          </div>
          <div [class.ok]="profilesStatus.envFileExists" [class.bad]="!profilesStatus.envFileExists">
            <strong>Config</strong>
            <span>{{ profilesStatus.envFileExists ? 'Loaded' : 'Missing' }}</span>
          </div>
          <div>
            <strong>Proxies</strong>
            <span>{{ profilesStatus.configuredProfileCount ?? 0 }}/{{ profilesStatus.profiles?.length ?? 0 }}</span>
          </div>
          <div>
            <strong>Running</strong>
            <span>{{ profilesStatus.runningProfileCount ?? 0 }}</span>
          </div>
          <div>
            <strong>Logged in</strong>
            <span>{{ profilesStatus.loggedInProfileCount ?? 0 }}</span>
          </div>
        </div>

        <p class="feedback update" *ngIf="updateMessage" [class.error]="updateError">
          {{ updateMessage }}
          <a *ngIf="updateStatus?.releaseUrl" [href]="updateStatus?.releaseUrl" target="_blank" rel="noopener">Open release</a>
        </p>

        <div class="profile-launcher">
          <div class="profile-controls">
            <label class="field profile-count">
              <span>Profiles to open</span>
              <div class="profile-count-row">
                <input
                  class="form-range"
                  [(ngModel)]="profileCount"
                  type="range"
                  min="1"
                  [max]="maxProfileCount()"
                  step="1"
                />
                <input
                  class="form-control form-control-sm count-input"
                  [(ngModel)]="profileCount"
                  type="number"
                  min="1"
                  [max]="maxProfileCount()"
                />
              </div>
            </label>
            <div class="delay-grid">
            <label class="field">
              <span>Delay from</span>
              <input class="form-control form-control-sm" [(ngModel)]="profilesMinDelay" type="number" min="0" max="3600" />
            </label>
            <label class="field">
              <span>Delay to</span>
              <input class="form-control form-control-sm" [(ngModel)]="profilesMaxDelay" type="number" min="0" max="3600" />
            </label>
            </div>
          </div>
          <div class="profiles-actions">
            <button class="btn btn-outline-primary btn-sm" type="button" [disabled]="profilesBusy" (click)="startProfiles()">
              {{ profilesBusy ? 'Starting...' : 'Start profiles' }}
            </button>
            <button class="btn btn-outline-success btn-sm" type="button" [disabled]="profilesBusy" (click)="openLoginQueue()">
              Login mode
            </button>
            <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="profilesBusy" (click)="refreshProfilesStatus()">Status</button>
          </div>
        </div>

        <p class="feedback" *ngIf="message" [class.error]="error">{{ message }}</p>
        <p class="feedback" *ngIf="checkMessage" [class.error]="checkError">{{ checkMessage }}</p>
        <p class="feedback" *ngIf="profilesMessage" [class.error]="profilesError">{{ profilesMessage }}</p>

        <div class="check-list" *ngIf="urlCheckStatus?.results?.length">
          <div class="check-row" *ngFor="let result of urlCheckStatus?.results" [class.ok]="result.ok" [class.bad]="!result.ok">
            <strong>{{ result.name }}</strong>
            <span>{{ result.ok ? 'OK' : result.reason }}</span>
            <small>{{ result.status || 'No status' }}{{ result.location ? ' -> ' + result.location : '' }}</small>
          </div>
        </div>

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
            <dd>{{ videoLabel(status) }}</dd>
          </div>
          <div *ngIf="status.currentTime !== undefined && status.currentTime !== null">
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

        <pre class="log-tail" *ngIf="status?.logTail">{{ status?.logTail }}</pre>

        <div class="profile-list" *ngIf="profilesStatus?.profiles?.length">
          <div class="profile-row" *ngFor="let profile of profilesStatus?.profiles">
            <div class="profile-id">
              <strong>{{ profile.name }}</strong>
              <small *ngIf="profile.label && profile.label !== profile.name">{{ profile.label }}</small>
            </div>
            <span class="login-pill" [class.logged-in]="isLoggedIn(profile)" [class.not-logged-in]="!isLoggedIn(profile)">
              {{ isLoggedIn(profile) ? 'Logged in' : 'Not logged in' }}
            </span>
            <span>
              {{ accountLabel(profile) }}
              <small class="profile-meta">
                {{ proxyLabel(profile) }} | {{ isRunning(profile) ? 'Running' : 'Stopped' }}{{ profile.lastUrl ? ' | ' + compactUrl(profile.lastUrl) : '' }}
              </small>
            </span>
            <div class="profile-row-actions">
              <button class="btn btn-outline-primary btn-sm" type="button" [disabled]="busyProfileName === profile.name" (click)="openProfile(profile.name)">
                {{ busyProfileName === profile.name ? 'Opening...' : 'Open' }}
              </button>
              <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="busyProfileName === profile.name || !isRunning(profile)" (click)="focusProfile(profile.name)">
                Focus
              </button>
              <button class="btn btn-outline-warning btn-sm" type="button" [disabled]="busyProfileName === profile.name" (click)="restartProfile(profile.name)">
                Restart
              </button>
              <button class="btn btn-outline-danger btn-sm" type="button" [disabled]="busyProfileName === profile.name || !isRunning(profile)" (click)="closeProfile(profile.name)">
                Close
              </button>
              <button class="btn btn-outline-success btn-sm" type="button" [disabled]="busyProfileName === profile.name" (click)="openLoginProfile(profile.name)">
                Login
              </button>
              <button
                class="btn btn-sm"
                [class.btn-outline-success]="!isLoggedIn(profile)"
                [class.btn-outline-secondary]="isLoggedIn(profile)"
                type="button"
                [disabled]="busyLoginStatusName === profile.name"
                (click)="setLoginStatus(profile.name, !isLoggedIn(profile))"
              >
                {{ busyLoginStatusName === profile.name ? 'Saving...' : isLoggedIn(profile) ? 'Mark not logged in' : 'Mark logged in' }}
              </button>
            </div>
          </div>
        </div>

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
    .profile-launcher { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin-top: 8px; padding-top: 8px; border-top: 1px dashed #dde3ea; }
    .profile-controls { display: grid; gap: 8px; min-width: min(100%, 420px); }
    .profile-count-row { display: grid; grid-template-columns: minmax(0, 1fr) 76px; gap: 10px; align-items: end; }
    .count-input { text-align: center; }
    .delay-grid { display: grid; grid-template-columns: repeat(2, 96px); gap: 8px; }
    .profiles-actions { display: flex; align-items: end; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
    .feedback {
      margin: 10px 0 0;
      padding: 8px 10px;
      border-radius: 8px;
      background: #e9f7ef;
      color: #146c43;
      font: 700 13px/1.35 "Segoe UI", sans-serif;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }
    .feedback.error { background: #fff1f2; color: #be123c; }
    .desktop-status { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; margin: 12px 0 0; }
    .desktop-status div { display: grid; gap: 2px; padding: 9px 10px; border: 1px solid #dde3ea; border-radius: 8px; background: #f8fafc; }
    .desktop-status div.ok { border-color: #bbf7d0; background: #f0fdf4; }
    .desktop-status div.bad { border-color: #fecdd3; background: #fff1f2; }
    .desktop-status strong { font: 800 11px/1.2 "Segoe UI", sans-serif; color: #64748b; text-transform: uppercase; letter-spacing: 0.04em; }
    .desktop-status span { font: 800 13px/1.2 "Segoe UI", sans-serif; color: #17212b; overflow-wrap: anywhere; }
    .check-list { display: grid; gap: 6px; margin: 12px 0 0; }
    .check-row { display: grid; grid-template-columns: 70px minmax(0, 1fr) minmax(180px, 1.2fr); gap: 8px; align-items: center; padding: 8px 10px; border: 1px solid #dde3ea; border-radius: 10px; background: #f8fafc; color: #17212b; font: 700 12px/1.3 "Segoe UI", sans-serif; }
    .check-row.ok { border-color: #bbf7d0; background: #f0fdf4; }
    .check-row.bad { border-color: #fecdd3; background: #fff1f2; }
    .check-row span, .check-row small { overflow-wrap: anywhere; }
    .check-row small { color: #64748b; font-weight: 600; }
    .status { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin: 12px 0 0; }
    .status div { padding: 10px; border: 1px solid #dde3ea; border-radius: 10px; background: #f8fafc; }
    .status dd { margin: 0; color: #17212b; font: 800 14px/1.3 "Segoe UI", sans-serif; overflow-wrap: anywhere; }
    .profile-list { display: grid; gap: 6px; margin: 12px 0 0; }
    .profile-row { display: grid; grid-template-columns: 140px 104px minmax(0, 1fr) 360px; gap: 8px; align-items: center; padding: 8px 10px; border: 1px solid #dde3ea; border-radius: 10px; background: #f8fafc; color: #17212b; font: 700 12px/1.3 "Segoe UI", sans-serif; }
    .profile-id { display: grid; gap: 2px; min-width: 0; }
    .profile-id strong, .profile-id small { overflow-wrap: anywhere; }
    .profile-id small { color: #64748b; font-weight: 700; }
    .login-pill { justify-self: start; padding: 5px 8px; border-radius: 999px; border: 1px solid #cbd5e1; background: #f8fafc; color: #475569; font: 800 11px/1 "Segoe UI", sans-serif; white-space: nowrap; }
    .login-pill.logged-in { border-color: #86efac; background: #f0fdf4; color: #15803d; }
    .login-pill.not-logged-in { border-color: #fecdd3; background: #fff1f2; color: #be123c; }
    .profile-row > span:not(.login-pill) { color: #64748b; overflow-wrap: anywhere; }
    .profile-meta { display: block; margin-top: 2px; color: #475569; font-weight: 700; }
    .profile-row-actions { display: flex; justify-content: flex-end; gap: 6px; flex-wrap: wrap; }
    .log-tail { margin: 12px 0 0; max-height: 220px; overflow: auto; padding: 10px; border-radius: 10px; background: #0f172a; color: #dbeafe; font: 600 12px/1.45 Consolas, monospace; white-space: pre-wrap; }
    @media (max-width: 760px) { .page-head, .slider-row, .status, .profile-row, .desktop-status { grid-template-columns: 1fr; display: grid; } .actions button, .profiles-actions button { width: 100%; } }
  `]
})
export class PlaybackPageComponent implements OnInit {
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
  protected profilesMinDelay = 20;
  protected profilesMaxDelay = 90;
  protected profileCount = 1;
  protected checkBusy = false;
  protected checkMessage = '';
  protected checkError = false;
  protected urlCheckStatus: ChromeProfilesUrlCheckStatus | null = null;
  protected busyProfileName = '';
  protected busyLoginStatusName = '';
  protected updateBusy = false;
  protected updateMessage = '';
  protected updateError = false;
  protected updateStatus: DesktopUpdateStatus | null = null;

  ngOnInit(): void {
    this.refreshProfilesStatus(false);
  }

  protected play(): void {
    const cleanUrl = this.normalizedUrl();
    if (!cleanUrl) {
      this.message = 'Add a video or website URL first.';
      this.error = true;
      return;
    }
    this.busy = true;
    this.error = false;
    this.message = `Opening ${this.normalizedProfileCount()} profile(s)...`;
    this.dashboardService.startAllChromeProfiles({
      minDelaySeconds: this.normalizedDelay(this.profilesMinDelay, 0),
      maxDelaySeconds: this.normalizedDelay(this.profilesMaxDelay, this.normalizedDelay(this.profilesMinDelay, 0)),
      profileCount: this.normalizedProfileCount(),
      url: cleanUrl
    }).subscribe({
      next: (status) => {
        this.profilesStatus = status;
        this.clampProfileCount();
        this.message = status.message || `Opened ${status.profileCount ?? this.normalizedProfileCount()} profile(s).`;
        this.error = false;
        this.busy = false;
      },
      error: (error) => {
        this.message = this.errorMessage(error, 'Could not open Chrome profiles.');
        this.error = true;
        this.busy = false;
        this.loadProfilesDiagnostics('message', this.message);
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
        this.message = this.errorMessage(error, 'Could not stop playback.');
        this.error = true;
        this.busy = false;
      }
    });
  }

  protected refreshStatus(): void {
    this.dashboardService.getYoutubeStatus().subscribe((status) => {
      this.status = status;
      this.message = status.lastError || 'Status refreshed.';
      this.error = status.status === 'error' || Boolean(status.lastError);
    });
  }

  protected startProfiles(): void {
    this.profilesBusy = true;
    this.profilesError = false;
    this.profilesMessage = 'Starting Chrome profiles...';
    this.dashboardService.startAllChromeProfiles({
      minDelaySeconds: this.normalizedDelay(this.profilesMinDelay, 0),
      maxDelaySeconds: this.normalizedDelay(this.profilesMaxDelay, this.normalizedDelay(this.profilesMinDelay, 0)),
      profileCount: this.normalizedProfileCount()
    }).subscribe({
      next: (status) => {
        this.profilesStatus = status;
        this.clampProfileCount();
        this.profilesMessage = status.message || 'Chrome profiles started.';
        this.profilesError = false;
        this.profilesBusy = false;
      },
      error: (error) => {
        this.profilesMessage = this.errorMessage(error, 'Could not start Chrome profiles.');
        this.profilesError = true;
        this.profilesBusy = false;
        this.loadProfilesDiagnostics('profilesMessage', this.profilesMessage);
      }
    });
  }

  protected checkUrl(): void {
    const cleanUrl = this.normalizedUrl();
    if (!cleanUrl) {
      this.checkMessage = 'Add a URL first.';
      this.checkError = true;
      return;
    }
    this.checkBusy = true;
    this.checkError = false;
    this.checkMessage = 'Checking URL through proxy profiles...';
    this.dashboardService.checkChromeProfilesUrl({ url: cleanUrl }).subscribe({
      next: (status) => {
        this.urlCheckStatus = status;
        this.checkMessage = `URL check: ${status.okCount}/${status.totalCount} profile(s) OK.`;
        this.checkError = status.okCount === 0;
        this.checkBusy = false;
      },
      error: (error) => {
        this.checkMessage = this.errorMessage(error, 'Could not check URL.');
        this.checkError = true;
        this.checkBusy = false;
        this.loadProfilesDiagnostics('checkMessage', this.checkMessage);
      }
    });
  }

  protected openCheckedProfiles(): void {
    const cleanUrl = this.normalizedUrl();
    const profileNames = this.checkedProfileNames().slice(0, this.normalizedProfileCount());
    if (!cleanUrl) {
      this.message = 'Add a video or website URL first.';
      this.error = true;
      return;
    }
    if (!profileNames.length) {
      this.message = 'Run Check URL first and use at least one OK profile.';
      this.error = true;
      return;
    }

    this.busy = true;
    this.error = false;
    this.message = `Opening ${profileNames.length} checked profile(s)...`;
    this.dashboardService.startAllChromeProfiles({
      minDelaySeconds: this.normalizedDelay(this.profilesMinDelay, 0),
      maxDelaySeconds: this.normalizedDelay(this.profilesMaxDelay, this.normalizedDelay(this.profilesMinDelay, 0)),
      profileCount: this.normalizedProfileCount(),
      profileNames,
      url: cleanUrl
    }).subscribe({
      next: (status) => {
        this.profilesStatus = status;
        this.profileCount = profileNames.length;
        this.message = status.message || `Opened ${profileNames.length} checked profile(s).`;
        this.error = false;
        this.busy = false;
      },
      error: (error) => {
        this.message = this.errorMessage(error, 'Could not open checked profiles.');
        this.error = true;
        this.busy = false;
        this.loadProfilesDiagnostics('message', this.message);
      }
    });
  }

  protected openProfile(profileName: string): void {
    const cleanUrl = this.normalizedUrl() || 'https://www.youtube.com/';
    this.busyProfileName = profileName;
    this.profilesError = false;
    this.profilesMessage = `Opening ${profileName}...`;
    this.dashboardService.startAllChromeProfiles({
      minDelaySeconds: 0,
      maxDelaySeconds: 0,
      profileCount: 1,
      profileNames: [profileName],
      url: cleanUrl
    }).subscribe({
      next: (status) => {
        this.profilesStatus = status;
        this.profileCount = 1;
        this.profilesMessage = `Opened ${profileName}.`;
        this.profilesError = false;
        this.busyProfileName = '';
      },
      error: (error) => {
        this.profilesMessage = this.errorMessage(error, `Could not open ${profileName}.`);
        this.profilesError = true;
        this.busyProfileName = '';
        this.loadProfilesDiagnostics('profilesMessage', this.profilesMessage);
      }
    });
  }

  protected focusProfile(profileName: string): void {
    this.runProfileAction(profileName, 'Focusing', () => this.dashboardService.focusChromeProfile(profileName));
  }

  protected closeProfile(profileName: string): void {
    this.runProfileAction(profileName, 'Closing', () => this.dashboardService.closeChromeProfile(profileName));
  }

  protected restartProfile(profileName: string): void {
    const cleanUrl = this.normalizedUrl() || 'https://www.youtube.com/';
    this.runProfileAction(profileName, 'Restarting', () => this.dashboardService.restartChromeProfile(profileName, cleanUrl));
  }

  protected openLoginProfile(profileName: string): void {
    this.runProfileAction(profileName, 'Opening login mode for', () => this.dashboardService.openChromeProfileLogin(profileName));
  }

  protected openLoginQueue(): void {
    const candidates = (this.profilesStatus?.profiles ?? [])
      .filter((profile) => !this.isLoggedIn(profile))
      .slice(0, this.normalizedProfileCount());
    if (!candidates.length) {
      this.profilesMessage = 'All selected profiles are already marked logged in.';
      this.profilesError = false;
      return;
    }
    this.profilesBusy = true;
    this.profilesMessage = `Opening login mode for ${candidates.length} profile(s)...`;
    this.openLoginProfileSequence(candidates.map((profile) => profile.name), 0);
  }

  protected checkForUpdates(): void {
    this.updateBusy = true;
    this.updateError = false;
    this.updateMessage = 'Checking latest Windows release...';
    this.dashboardService.getDesktopUpdateStatus().subscribe({
      next: (status) => {
        this.updateStatus = status;
        this.updateBusy = false;
        this.updateError = Boolean(status.error);
        if (status.error) {
          this.updateMessage = status.error;
          return;
        }
        if (status.updateAvailable) {
          this.updateMessage = `Update available: ${status.latestVersion}. Open release: ${status.releaseUrl}`;
        } else {
          this.updateMessage = `You are on the latest version: ${status.currentVersion || status.latestVersion}.`;
        }
      },
      error: (error) => {
        this.updateBusy = false;
        this.updateError = true;
        this.updateMessage = this.errorMessage(error, 'Could not check for updates.');
      }
    });
  }

  protected setLoginStatus(profileName: string, loggedIn: boolean): void {
    this.busyLoginStatusName = profileName;
    this.profilesError = false;
    this.profilesMessage = `Saving ${profileName} login status...`;
    this.dashboardService.updateChromeProfileLoginStatus(profileName, loggedIn).subscribe({
      next: (status) => {
        this.profilesStatus = status;
        this.profilesMessage = `${profileName} marked ${loggedIn ? 'logged in' : 'not logged in'}.`;
        this.profilesError = false;
        this.busyLoginStatusName = '';
      },
      error: (error) => {
        this.profilesMessage = this.errorMessage(error, `Could not update ${profileName}.`);
        this.profilesError = true;
        this.busyLoginStatusName = '';
      }
    });
  }

  protected refreshProfilesStatus(showMessage = true): void {
    this.profilesBusy = true;
    this.dashboardService.getChromeProfilesStatus().subscribe({
      next: (status) => {
        this.profilesStatus = status;
        this.clampProfileCount();
        if (showMessage) {
          this.profilesMessage = status.envFileExists ? `Found ${status.profiles?.length ?? 0} profile(s).` : 'profiles.env is missing on the server.';
        }
        this.profilesError = !status.scriptExists || !status.envFileExists;
        this.profilesBusy = false;
      },
      error: (error) => {
        if (showMessage) {
          this.profilesMessage = this.errorMessage(error, 'Could not read profile launcher status.');
        }
        this.profilesError = true;
        this.profilesBusy = false;
      }
    });
  }

  protected playbackLabel(status: YoutubePlaybackStatus): string {
    if (status.automationMode === 'desktop') {
      return status.status === 'idle' ? 'idle' : 'desktop browser';
    }
    if (status.status === 'idle') {
      return 'idle';
    }
    if (!status.videoPresent) {
      return status.status;
    }
    return status.paused ? 'paused' : 'playing';
  }

  protected videoLabel(status: YoutubePlaybackStatus): string {
    if (status.automationMode === 'desktop') {
      return 'Desktop mode';
    }
    return status.videoPresent ? 'Detected' : 'Not found';
  }

  protected isYoutubeUrl(): boolean {
    const value = this.url.trim();
    if (!value) {
      return false;
    }
    try {
      const host = new URL(value).hostname.toLowerCase();
      return host === 'youtube.com' || host.endsWith('.youtube.com') || host === 'youtu.be' || host.endsWith('.youtu.be');
    } catch {
      return false;
    }
  }

  protected checkedProfileNames(): string[] {
    if (!this.urlCheckStatus || this.urlCheckStatus.url !== this.normalizedUrl()) {
      return [];
    }
    return this.urlCheckStatus.results.filter((result) => result.ok).map((result) => result.name);
  }

  protected isLoggedIn(profile: { googleAccount?: string; loggedIn?: boolean | string; loginStatus?: string }): boolean {
    return Boolean(profile.googleAccount?.trim()) || profile.loggedIn === true || profile.loggedIn === 'true' || profile.loginStatus === 'logged_in';
  }

  protected isRunning(profile: { running?: boolean | string }): boolean {
    return profile.running === true || profile.running === 'true';
  }

  protected compactUrl(value: string): string {
    if (!value) {
      return '';
    }
    try {
      const url = new URL(value);
      return `${url.hostname}${url.pathname === '/' ? '' : url.pathname}`;
    } catch {
      return value.length > 42 ? `${value.slice(0, 39)}...` : value;
    }
  }

  protected accountLabel(profile: { googleAccount?: string; googleAccountName?: string }): string {
    const email = profile.googleAccount?.trim();
    const name = profile.googleAccountName?.trim();
    if (email && name && name !== email) {
      return `${name} (${email})`;
    }
    return email || 'Google account not detected';
  }

  protected proxyLabel(profile: { proxy?: string; upstreamProxy?: string }): string {
    return profile.proxy || profile.upstreamProxy || 'Proxy not set';
  }

  private normalizedPercent(): number {
    const numeric = Number(this.percent);
    if (!Number.isFinite(numeric)) {
      return 100;
    }
    return Math.max(0, Math.min(100, Math.round(numeric)));
  }

  private normalizedDelay(value: number, fallback: number): number {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return fallback;
    }
    return Math.max(0, Math.min(3600, Math.round(numeric)));
  }

  protected maxProfileCount(): number {
    return Math.max(1, this.profilesStatus?.profiles?.length ?? 1);
  }

  protected normalizedProfileCount(): number {
    const numeric = Number(this.profileCount);
    if (!Number.isFinite(numeric)) {
      return 1;
    }
    return Math.max(1, Math.min(this.maxProfileCount(), Math.round(numeric)));
  }

  private clampProfileCount(): void {
    this.profileCount = this.normalizedProfileCount();
  }

  private normalizedUrl(): string {
    const value = this.url.trim();
    if (!value) {
      return '';
    }
    try {
      const parsed = new URL(value);
      const host = parsed.hostname.toLowerCase();
      if (host === 'youtu.be' || host.endsWith('.youtu.be')) {
        const videoId = parsed.pathname.replace(/^\//, '').split('/')[0];
        return videoId ? `https://www.youtube.com/watch?v=${videoId}` : value;
      }
      if (host === 'youtube.com' || host.endsWith('.youtube.com')) {
        const videoId = parsed.searchParams.get('v');
        if (videoId) {
          const normalized = new URL('https://www.youtube.com/watch');
          normalized.searchParams.set('v', videoId);
          const list = parsed.searchParams.get('list');
          if (list) {
            normalized.searchParams.set('list', list);
          }
          return normalized.toString();
        }
      }
    } catch {
      return value;
    }
    return value;
  }

  private runProfileAction(profileName: string, verb: string, action: () => ReturnType<DashboardService['focusChromeProfile']>): void {
    this.busyProfileName = profileName;
    this.profilesError = false;
    this.profilesMessage = `${verb} ${profileName}...`;
    action().subscribe({
      next: (status) => {
        this.profilesStatus = status;
        this.profilesMessage = status.message || `${verb} ${profileName}.`;
        this.profilesError = false;
        this.busyProfileName = '';
      },
      error: (error) => {
        this.profilesMessage = this.errorMessage(error, `Could not update ${profileName}.`);
        this.profilesError = true;
        this.busyProfileName = '';
        this.loadProfilesDiagnostics('profilesMessage', this.profilesMessage);
      }
    });
  }

  private openLoginProfileSequence(profileNames: string[], index: number): void {
    if (index >= profileNames.length) {
      this.profilesBusy = false;
      this.profilesMessage = `Opened login mode for ${profileNames.length} profile(s).`;
      this.refreshProfilesStatus(false);
      return;
    }
    const profileName = profileNames[index];
    this.dashboardService.openChromeProfileLogin(profileName).subscribe({
      next: (status) => {
        this.profilesStatus = status;
        setTimeout(() => this.openLoginProfileSequence(profileNames, index + 1), 1500);
      },
      error: (error) => {
        this.profilesBusy = false;
        this.profilesMessage = this.errorMessage(error, `Could not open login mode for ${profileName}.`);
        this.profilesError = true;
      }
    });
  }

  private errorMessage(error: unknown, fallback: string): string {
    const candidate = error as {
      error?: unknown;
      message?: string;
      status?: number;
      statusText?: string;
    };
    const nested = candidate?.error;
    if (typeof nested === 'string' && nested.trim()) {
      try {
        const parsed = JSON.parse(nested) as { message?: string; error?: string };
        if (parsed.message?.trim()) {
          return parsed.message;
        }
        if (parsed.error?.trim() && !this.isGenericServerError(parsed.error)) {
          return parsed.error;
        }
      } catch {
        return nested;
      }
    }
    if (nested && typeof nested === 'object') {
      const nestedObject = nested as { message?: string; error?: string };
      if (nestedObject.message?.trim()) {
        return nestedObject.message;
      }
      if (nestedObject.error?.trim() && !this.isGenericServerError(nestedObject.error)) {
        return nestedObject.error;
      }
    }
    if (candidate?.message?.trim()) {
      if (candidate.message.startsWith('Http failure response')) {
        if (candidate.status) {
          return `${fallback} (${candidate.status}${candidate.statusText ? ' ' + candidate.statusText : ''})`;
        }
        return fallback;
      }
      if (candidate.status && candidate.statusText) {
        return `${candidate.message}: ${candidate.status} ${candidate.statusText}`;
      }
      return candidate.message;
    }
    return fallback;
  }

  private loadProfilesDiagnostics(target: 'message' | 'profilesMessage' | 'checkMessage', baseMessage: string): void {
    this.dashboardService.getChromeProfilesStatus().subscribe({
      next: (status) => {
        this.profilesStatus = status;
        const diagnostics = this.profileDiagnostics(status);
        this.setDiagnosticMessage(target, diagnostics ? `${baseMessage}\n\n${diagnostics}` : baseMessage);
      },
      error: () => this.setDiagnosticMessage(target, baseMessage)
    });
  }

  private setDiagnosticMessage(target: 'message' | 'profilesMessage' | 'checkMessage', value: string): void {
    if (target === 'message') {
      this.message = value;
      this.error = true;
      return;
    }
    if (target === 'profilesMessage') {
      this.profilesMessage = value;
      this.profilesError = true;
      return;
    }
    this.checkMessage = value;
    this.checkError = true;
  }

  private profileDiagnostics(status: ChromeProfilesStatus): string {
    const lines = [
      'Profile launcher diagnostics:',
      `Runtime directory: ${status.directory || 'unknown'} (${status.directoryExists ? 'exists' : 'missing'})`,
      `profiles.env: ${status.envFile || 'unknown'} (${status.envFileExists ? 'exists' : 'missing'})`,
      `Launcher script: ${status.script || 'unknown'} (${status.scriptExists ? 'exists' : 'missing'})`,
      `Profile script: ${status.startProfileScript || 'unknown'} (${status.startProfileScriptExists ? 'exists' : 'missing'})`
    ];
    const logTail = (status.logTail || '').trim();
    lines.push(logTail ? `Last launcher log:\n${logTail}` : 'Last launcher log: empty');
    return lines.join('\n');
  }

  private isGenericServerError(message: string): boolean {
    return ['Internal Server Error', 'Http failure response'].includes(message.trim());
  }
}
