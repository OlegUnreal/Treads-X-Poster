import { DecimalPipe, NgFor, NgIf } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChromeProfileSummary, ChromeProfilesStatus, ChromeProfilesUrlCheckStatus, DesktopUpdateStatus, YoutubePlaybackStatus } from '../models/dashboard.models';
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
          <h1>Behind The Smile Playback</h1>
        </div>
        <span class="mode-pill">{{ isYoutubeUrl() ? 'YouTube' : 'Website' }}</span>
      </header>

      <article class="panel">
        <div class="hero-control">
          <label class="field url-field">
            <span>Video or website URL</span>
            <input class="form-control form-control-lg" [(ngModel)]="url" placeholder="https://youtube.com/watch?v=..." />
          </label>

          <div class="quick-settings">
            <label class="field count-field">
              <span>Profiles</span>
              <input class="form-control form-control-lg" [(ngModel)]="profileCount" type="number" min="1" [max]="maxProfileCount()" />
            </label>
            <label class="field mode-field">
              <span>Mode</span>
              <select class="form-select form-select-lg" [(ngModel)]="launchMode" (ngModelChange)="applyLaunchMode()">
                <option value="youtube">YT</option>
                <option value="pornhub">PH</option>
              </select>
            </label>
          </div>

          <button class="primary-launch" type="button" [disabled]="busy || profilesBusy" (click)="play()">
            {{ busy || profilesBusy ? 'Starting...' : 'Start ' + normalizedProfileCount() + ' profiles' }}
          </button>
        </div>

        <div class="status-bar" *ngIf="profilesStatus">
          <span [class.bad]="profilesStatus.chromeFound === false">{{ profilesStatus.chromeFound ? 'Chrome ready' : 'Chrome missing' }}</span>
          <span [class.bad]="!profilesStatus.envFileExists">{{ profilesStatus.envFileExists ? 'Config loaded' : 'Config missing' }}</span>
          <span>{{ profilesStatus.configuredProfileCount ?? 0 }} proxies</span>
          <span>{{ profilesStatus.loggedInProfileCount ?? 0 }} logged in</span>
          <span>{{ profilesStatus.runningProfileCount ?? 0 }} running</span>
        </div>

        <p class="feedback update" *ngIf="updateMessage" [class.error]="updateError">
          {{ updateMessage }}
          <a *ngIf="updateStatus?.releaseUrl" [href]="updateStatus?.releaseUrl" target="_blank" rel="noopener">Open release</a>
        </p>

        <div class="secondary-actions">
          <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="profilesBusy" (click)="refreshProfilesStatus()">Refresh</button>
          <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="busy" (click)="stop()">Stop playback</button>
          <button class="btn btn-outline-danger btn-sm" type="button" [disabled]="busy" (click)="closeAllProfiles()">Close all</button>
          <button class="btn btn-outline-success btn-sm" type="button" [disabled]="profilesBusy" (click)="openLoginQueue()">Login setup</button>
          <button class="btn btn-outline-secondary btn-sm" type="button" (click)="showAdvanced = !showAdvanced">
            {{ showAdvanced ? 'Hide advanced' : 'Advanced' }}
          </button>
        </div>

        <div class="advanced-panel" *ngIf="showAdvanced">
          <div class="settings-grid">
            <label class="field" *ngIf="isYoutubeUrl()">
              <span>Play percent</span>
              <input class="form-control form-control-sm" [(ngModel)]="percent" type="number" min="0" max="100" />
            </label>
            <label class="field">
              <span>Delay from</span>
              <input class="form-control form-control-sm" [(ngModel)]="profilesMinDelay" type="number" min="0" max="3600" />
            </label>
            <label class="field">
              <span>Delay to</span>
              <input class="form-control form-control-sm" [(ngModel)]="profilesMaxDelay" type="number" min="0" max="3600" />
            </label>
            <label class="field">
              <span>Referer header</span>
              <input class="form-control form-control-sm" [(ngModel)]="refererHeader" placeholder="https://youtube.com/" />
            </label>
            <label class="field">
              <span>Video quality</span>
              <select class="form-select form-select-sm" [(ngModel)]="videoQuality">
                <option *ngFor="let option of videoQualityOptions" [ngValue]="option.value">{{ option.label }}</option>
              </select>
            </label>
          </div>

          <div class="actions">
            <button class="btn btn-outline-primary btn-sm" type="button" [disabled]="checkBusy" (click)="checkUrl()">
              {{ checkBusy ? 'Checking...' : 'Check URL' }}
            </button>
            <button class="btn btn-outline-success btn-sm" type="button" [disabled]="busy || !checkedProfileNames().length" (click)="openCheckedProfiles()">
              Open checked
            </button>
            <button class="btn btn-outline-warning btn-sm" type="button" [disabled]="busy || !checkedProfileNames().length" (click)="restartCheckedProfiles()">
              Restart checked
            </button>
            <button class="btn btn-outline-danger btn-sm" type="button" [disabled]="busy || !checkedProfileNames().length" (click)="closeCheckedProfiles()">
              Close checked
            </button>
            <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="updateBusy" (click)="checkForUpdates()">
              {{ updateBusy ? 'Checking update...' : 'Check update' }}
            </button>
          </div>
        </div>

        <p class="feedback" *ngIf="message" [class.error]="error">{{ compactFeedback(message) }}</p>
        <p class="feedback" *ngIf="checkMessage" [class.error]="checkError">{{ compactFeedback(checkMessage) }}</p>
        <div class="check-progress" *ngIf="urlCheckStatus && urlCheckStatus.url === normalizedUrl()">
          <div class="check-progress-head">
            <span>{{ checkCompletedCount() }}/{{ checkTotalCount() }} checked</span>
            <span>{{ urlCheckStatus.okCount }} OK</span>
          </div>
          <div class="check-progress-track">
            <span [style.width.%]="checkProgressPercent()"></span>
          </div>
        </div>
        <p class="feedback" *ngIf="profilesMessage" [class.error]="profilesError">{{ compactFeedback(profilesMessage) }}</p>

        <div class="profile-section" *ngIf="profilesStatus?.profiles?.length">
          <div class="profile-section-head">
            <h2>Profiles</h2>
            <div class="profile-tabs">
              <button type="button" [class.active]="profileFilter === 'all'" (click)="profileFilter = 'all'">All</button>
              <button type="button" [class.active]="profileFilter === 'running'" (click)="profileFilter = 'running'">Running</button>
              <button type="button" [class.active]="profileFilter === 'login'" (click)="profileFilter = 'login'">Needs login</button>
              <button type="button" [class.active]="profileFilter === 'proxy'" (click)="profileFilter = 'proxy'">Proxy issues</button>
            </div>
          </div>
          <p class="empty-filter" *ngIf="!filteredProfiles().length">No profiles in this view.</p>
          <div
            class="profile-row"
            *ngFor="let profile of filteredProfiles()"
            [class.check-ok]="checkResultFor(profile.name)?.ok"
            [class.check-pending]="checkResultFor(profile.name)?.reason === 'Pending'"
            [class.check-bad]="checkResultFor(profile.name) && !checkResultFor(profile.name)?.ok && checkResultFor(profile.name)?.reason !== 'Pending'"
            [class.bulk-ok]="isBulkOk(bulkResultFor(profile.name)?.status || '')"
            [class.bulk-skip]="isBulkSkip(bulkResultFor(profile.name)?.status || '')"
          >
            <div class="profile-id">
              <strong>{{ profile.name }}</strong>
              <small *ngIf="profile.label && profile.label !== profile.name">{{ profile.label }}</small>
            </div>
            <div class="profile-main">
              <span>{{ accountLabel(profile) }}</span>
              <small>{{ profile.proxyKey || proxyLabel(profile) }}</small>
            </div>
            <div class="profile-pills">
              <span class="state-pill" [class.good]="isLoggedIn(profile)" [class.bad]="!isLoggedIn(profile)">
                {{ isLoggedIn(profile) ? 'Logged in' : 'Needs login' }}
              </span>
              <span class="state-pill" [class.good]="isRunning(profile)">
                {{ isRunning(profile) ? 'Running' : 'Stopped' }}
              </span>
              <span class="state-pill" [class.good]="supportsYoutube(profile)" [class.bad]="blocksYoutube(profile)">YT {{ capabilityShort(profile.supportsYoutube) }}</span>
              <span class="state-pill" [class.good]="supportsPornhub(profile)" [class.bad]="blocksPornhub(profile)">PH {{ capabilityShort(profile.supportsPornhub) }}</span>
            </div>
            <div class="profile-row-actions">
              <button class="btn btn-outline-primary btn-sm" type="button" [disabled]="busyProfileName === profile.name" (click)="isLoggedIn(profile) ? openProfile(profile.name) : openLoginProfile(profile.name)">
                {{ isLoggedIn(profile) ? 'Open' : 'Login' }}
              </button>
              <button class="btn btn-outline-danger btn-sm" type="button" [disabled]="busyProfileName === profile.name || !isRunning(profile)" (click)="closeProfile(profile.name)">
                Stop
              </button>
              <button class="btn btn-outline-secondary btn-sm" type="button" (click)="toggleProfileDetails(profile.name)">
                {{ expandedProfileName === profile.name ? 'Less' : 'More' }}
              </button>
            </div>
            <div class="profile-details" *ngIf="expandedProfileName === profile.name">
              <span>{{ profileGeo(profile) || 'No geo details' }}</span>
              <span>{{ isRunning(profile) ? 'Debug ' + profile.debugPort : 'Not running' }}{{ profile.lastUrl ? ' | ' + compactUrl(profile.lastUrl) : '' }}</span>
              <span *ngIf="checkResultFor(profile.name)">Check: {{ checkResultFor(profile.name)?.ok ? 'OK' : checkResultFor(profile.name)?.reason }}</span>
              <span *ngIf="bulkResultFor(profile.name)">Action: {{ bulkResultFor(profile.name)?.message || bulkResultFor(profile.name)?.status }}</span>
              <div class="detail-actions">
                <button class="btn btn-outline-primary btn-sm" type="button" [disabled]="busyProfileName === profile.name" (click)="openProfile(profile.name)">
                {{ busyProfileName === profile.name ? 'Opening...' : 'Open' }}
                </button>
                <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="busyProfileName === profile.name || !isRunning(profile)" (click)="focusProfile(profile.name)">Focus</button>
                <button class="btn btn-outline-warning btn-sm" type="button" [disabled]="busyProfileName === profile.name" (click)="restartProfile(profile.name)">Restart</button>
                <button class="btn btn-outline-success btn-sm" type="button" [disabled]="busyProfileName === profile.name" (click)="openLoginProfile(profile.name)">Login</button>
                <button class="btn btn-outline-secondary btn-sm" type="button" [disabled]="busyLoginStatusName === profile.name" (click)="setLoginStatus(profile.name, !isLoggedIn(profile))">
                  {{ isLoggedIn(profile) ? 'Mark not logged in' : 'Mark logged in' }}
                </button>
                <label class="capability-control">
                  <span>YT</span>
                  <select
                    class="form-select form-select-sm"
                    [disabled]="busyProxyCapabilityName === profile.name || !profile.proxyKey"
                    [ngModel]="capabilitySelectValue(profile.supportsYoutube)"
                    (ngModelChange)="setProxyCapability(profile, 'youtube', $event === 'true')"
                  >
                    <option value="" disabled>?</option>
                    <option value="true">ok</option>
                    <option value="false">no</option>
                  </select>
                </label>
                <label class="capability-control">
                  <span>PH</span>
                  <select
                    class="form-select form-select-sm"
                    [disabled]="busyProxyCapabilityName === profile.name || !profile.proxyKey"
                    [ngModel]="capabilitySelectValue(profile.supportsPornhub)"
                    (ngModelChange)="setProxyCapability(profile, 'pornhub', $event === 'true')"
                  >
                    <option value="" disabled>?</option>
                    <option value="true">ok</option>
                    <option value="false">no</option>
                  </select>
                </label>
              </div>
            </div>
          </div>
        </div>

        <pre class="log-tail" *ngIf="showAdvanced && profilesStatus?.logTail">{{ profilesStatus?.logTail }}</pre>
      </article>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .page { display: grid; gap: 12px; max-width: 960px; }
    .page-head { display: flex; justify-content: space-between; align-items: end; gap: 12px; }
    .page-head h1 { margin: 0; font-size: 26px; line-height: 1.1; }
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
      padding: 14px;
      background: #fff;
      border: 1px solid #dde3ea;
      border-radius: 12px;
      box-shadow: 0 8px 22px rgba(15, 23, 42, 0.05);
    }
    .field { display: grid; gap: 4px; }
    .hero-control { display: grid; grid-template-columns: minmax(280px, 1fr) 190px 180px; gap: 12px; align-items: end; }
    .quick-settings { display: grid; grid-template-columns: 86px 96px; gap: 8px; }
    .count-field input { text-align: center; }
    .primary-launch {
      min-height: 48px;
      border: 0;
      border-radius: 8px;
      background: #0d6efd;
      color: #fff;
      font: 800 15px/1.2 "Segoe UI", sans-serif;
      box-shadow: 0 8px 18px rgba(13, 110, 253, 0.18);
    }
    .primary-launch:disabled { opacity: 0.62; box-shadow: none; }
    .status-bar {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      margin-top: 12px;
      padding: 8px;
      border-radius: 10px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
    }
    .status-bar span {
      min-height: 28px;
      display: inline-flex;
      align-items: center;
      padding: 5px 9px;
      border-radius: 999px;
      background: #e9f7ef;
      color: #146c43;
      font: 800 12px/1.2 "Segoe UI", sans-serif;
      white-space: nowrap;
    }
    .status-bar span.bad { background: #fff1f2; color: #be123c; }
    .secondary-actions, .actions { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; margin-top: 12px; }
    .secondary-actions .btn, .actions .btn, .profile-row-actions .btn, .detail-actions .btn {
      min-height: 32px;
      min-width: 86px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      white-space: nowrap;
    }
    .advanced-panel {
      margin-top: 12px;
      padding: 12px;
      border: 1px solid #dbe3ee;
      border-radius: 10px;
      background: #f8fafc;
    }
    .settings-grid { display: grid; grid-template-columns: repeat(5, minmax(120px, 1fr)); gap: 10px; align-items: end; }
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
    .check-progress { margin: 10px 0 0; padding: 9px 12px; border: 1px solid #cbd5e1; border-radius: 10px; background: #f8fafc; }
    .check-progress-head { display: flex; justify-content: space-between; gap: 10px; margin-bottom: 7px; color: #334155; font: 800 12px/1.2 "Segoe UI", sans-serif; }
    .check-progress-track { height: 8px; border-radius: 999px; background: #e2e8f0; overflow: hidden; }
    .check-progress-track span { display: block; height: 100%; min-width: 0; border-radius: inherit; background: #2563eb; transition: width 180ms ease; }
    .profile-section { margin-top: 14px; display: grid; gap: 8px; }
    .profile-section-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
    .profile-section h2 { margin: 0; font-size: 18px; line-height: 1.2; }
    .profile-tabs { display: flex; gap: 4px; flex-wrap: wrap; padding: 3px; border: 1px solid #dbe3ee; border-radius: 10px; background: #f8fafc; }
    .profile-tabs button {
      min-height: 28px;
      border: 0;
      border-radius: 7px;
      background: transparent;
      color: #475569;
      font: 800 12px/1.1 "Segoe UI", sans-serif;
      padding: 5px 10px;
    }
    .profile-tabs button.active { background: #fff; color: #0f172a; box-shadow: 0 1px 4px rgba(15, 23, 42, 0.1); }
    .empty-filter { margin: 0; padding: 10px; color: #64748b; background: #f8fafc; border: 1px dashed #cbd5e1; border-radius: 10px; font: 700 13px/1.3 "Segoe UI", sans-serif; }
    .profile-row { display: grid; grid-template-columns: 88px minmax(180px, 1fr) minmax(250px, 1.15fr) 250px; gap: 8px; align-items: center; padding: 8px 10px; border: 1px solid #dde3ea; border-radius: 10px; background: #fff; color: #17212b; font: 700 12px/1.3 "Segoe UI", sans-serif; }
    .profile-row.check-ok { border-color: #86efac; background: #f0fdf4; }
    .profile-row.check-pending { border-color: #bfdbfe; background: #eff6ff; }
    .profile-row.check-bad { border-color: #fecdd3; background: #fff1f2; }
    .profile-row.check-ok .profile-id strong { color: #15803d; }
    .profile-row.check-pending .profile-id strong { color: #1d4ed8; }
    .profile-row.check-bad .profile-id strong { color: #be123c; }
    .profile-row.bulk-ok { box-shadow: inset 4px 0 0 #22c55e; }
    .profile-row.bulk-skip { box-shadow: inset 4px 0 0 #f59e0b; }
    .profile-id { display: grid; gap: 2px; min-width: 0; }
    .profile-id strong, .profile-id small { overflow-wrap: anywhere; }
    .profile-id small { color: #64748b; font-weight: 700; }
    .profile-main { display: grid; gap: 2px; min-width: 0; }
    .profile-main span, .profile-main small { overflow-wrap: anywhere; }
    .profile-main small { color: #64748b; font-weight: 700; }
    .profile-pills { display: flex; flex-wrap: wrap; gap: 5px; }
    .state-pill {
      min-height: 24px;
      display: inline-flex;
      align-items: center;
      padding: 4px 7px;
      border-radius: 999px;
      border: 1px solid #cbd5e1;
      background: #f8fafc;
      color: #475569;
      font: 800 11px/1 "Segoe UI", sans-serif;
      white-space: nowrap;
    }
    .state-pill.good { border-color: #86efac; background: #f0fdf4; color: #15803d; }
    .state-pill.bad { border-color: #fecdd3; background: #fff1f2; color: #be123c; }
    .profile-row-actions { display: flex; justify-content: flex-end; gap: 6px; flex-wrap: wrap; }
    .profile-details {
      grid-column: 1 / -1;
      display: grid;
      gap: 6px;
      padding-top: 8px;
      border-top: 1px dashed #dbe3ee;
      color: #475569;
      overflow-wrap: anywhere;
    }
    .detail-actions { display: flex; justify-content: flex-end; gap: 6px; flex-wrap: wrap; }
    .capability-control {
      min-height: 32px;
      display: inline-grid;
      grid-template-columns: 28px 76px;
      align-items: center;
      gap: 5px;
      margin: 0;
      color: #475569;
      font: 800 11px/1 "Segoe UI", sans-serif;
    }
    .capability-control .form-select { min-height: 32px; padding-top: 4px; padding-bottom: 4px; font-weight: 800; }
    .log-tail { margin: 12px 0 0; max-height: 220px; overflow: auto; padding: 10px; border-radius: 10px; background: #0f172a; color: #dbeafe; font: 600 12px/1.45 Consolas, monospace; white-space: pre-wrap; }
    @media (max-width: 980px) {
      .hero-control, .settings-grid, .profile-row { grid-template-columns: 1fr; }
      .quick-settings { grid-template-columns: 1fr 1fr; }
      .primary-launch, .secondary-actions .btn, .actions .btn, .profile-row-actions .btn { width: 100%; }
      .profile-row-actions, .detail-actions { justify-content: stretch; }
    }
    @media (max-width: 640px) {
      .page-head, .quick-settings { grid-template-columns: 1fr; display: grid; }
      .profile-tabs, .secondary-actions, .actions { display: grid; grid-template-columns: 1fr; }
    }
  `]
})
export class PlaybackPageComponent implements OnInit, OnDestroy {
  private readonly dashboardService = inject(DashboardService);
  private readonly launchSettingsStorageKey = 'bts-playback-launch-settings';

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
  protected busyProxyCapabilityName = '';
  protected updateBusy = false;
  protected updateMessage = '';
  protected updateError = false;
  protected updateStatus: DesktopUpdateStatus | null = null;
  protected refererHeader = '';
  protected videoQuality = 'auto';
  protected requireYoutubeProxy = false;
  protected requirePornhubProxy = false;
  protected showAdvanced = false;
  protected launchMode: 'youtube' | 'pornhub' = 'youtube';
  protected profileFilter: 'all' | 'running' | 'login' | 'proxy' = 'all';
  protected expandedProfileName = '';
  private bulkProgressTimer: ReturnType<typeof setTimeout> | null = null;
  private checkProgressTimer: ReturnType<typeof setTimeout> | null = null;
  protected readonly videoQualityOptions = [
    { value: 'auto', label: 'Auto' },
    { value: 'large', label: '480p' },
    { value: 'hd720', label: '720p' },
    { value: 'hd1080', label: '1080p' },
    { value: 'hd1440', label: '1440p' },
    { value: 'highres', label: 'Best available' },
    { value: 'medium', label: '360p' },
    { value: 'small', label: '240p' },
    { value: 'tiny', label: '144p' }
  ];

  ngOnInit(): void {
    this.loadLaunchSettings();
    this.syncLaunchModeFromFlags();
    this.refreshProfilesStatus(false);
  }

  ngOnDestroy(): void {
    this.stopBulkProgress();
    this.stopCheckProgress();
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
      url: cleanUrl,
      ...this.launchOptions(true)
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
    this.dashboardService.stopChromeProfileLaunch().subscribe({
      next: (profileStatus) => {
        this.profilesStatus = profileStatus;
        this.dashboardService.stopYoutube().subscribe({
          next: (status) => {
            this.status = status;
            this.message = 'Playback and pending profile launches stopped.';
            this.error = false;
            this.busy = false;
          },
          error: (error) => {
            this.message = this.errorMessage(error, 'Profile launch stopped, but playback stop failed.');
            this.error = true;
            this.busy = false;
          }
        });
      },
      error: (error) => {
        this.message = this.errorMessage(error, 'Could not stop pending profile launches.');
        this.error = true;
        this.busy = false;
      }
    });
  }

  protected closeAllProfiles(): void {
    this.busy = true;
    this.error = false;
    this.message = 'Closing all Chrome profiles...';
    this.dashboardService.closeAllChromeProfiles().subscribe({
      next: (status) => {
        this.profilesStatus = status;
        this.message = status.message || 'Closed all Chrome profiles.';
        this.error = false;
        this.busy = false;
      },
      error: (error) => {
        this.message = this.errorMessage(error, 'Could not close Chrome profiles.');
        this.error = true;
        this.busy = false;
        this.loadProfilesDiagnostics('message', this.message);
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
      profileCount: this.normalizedProfileCount(),
      ...this.launchOptions(true)
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
    this.stopCheckProgress();
    this.dashboardService.startChromeProfilesUrlCheck({ url: cleanUrl }).subscribe({
      next: (status) => {
        this.applyUrlCheckStatus(status);
        if (status.checking) {
          this.startCheckProgress();
        }
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
    this.runCheckedBulkAction('open');
  }

  protected restartCheckedProfiles(): void {
    this.runCheckedBulkAction('restart');
  }

  protected closeCheckedProfiles(): void {
    this.runCheckedBulkAction('close');
  }

  private runCheckedBulkAction(action: 'open' | 'restart' | 'close'): void {
    const cleanUrl = this.normalizedUrl();
    const profileNames = this.checkedProfileNames().slice(0, this.normalizedProfileCount());
    if (action !== 'close' && !cleanUrl) {
      this.message = 'Add a video or website URL first.';
      this.error = true;
      return;
    }
    if (!profileNames.length) {
      this.message = 'Run Check URL first and use at least one OK profile.';
      this.error = true;
      return;
    }

    this.stopBulkProgress();
    this.busy = true;
    this.error = false;
    const actionLabel = action === 'restart' ? 'Restarting' : action === 'close' ? 'Closing' : 'Opening';
    this.message = `${actionLabel} ${profileNames.length} checked profile(s)...`;
    this.dashboardService.bulkChromeProfiles({
      action,
      minDelaySeconds: this.normalizedDelay(this.profilesMinDelay, 0),
      maxDelaySeconds: this.normalizedDelay(this.profilesMaxDelay, this.normalizedDelay(this.profilesMinDelay, 0)),
      profileNames,
      url: cleanUrl || undefined,
      ...this.launchOptions(true)
    }).subscribe({
      next: (status) => {
        this.profilesStatus = status;
        this.profileCount = profileNames.length;
        this.message = `${status.message || `${actionLabel} checked profile(s).`} Progress: 0/${profileNames.length}.`;
        this.error = false;
        this.busy = false;
        this.startBulkProgress(profileNames, action, actionLabel, 0);
      },
      error: (error) => {
        this.message = this.errorMessage(error, `Could not ${action} checked profiles.`);
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
      url: cleanUrl,
      ...this.launchOptions()
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
    this.runProfileAction(profileName, 'Restarting', () => this.dashboardService.restartChromeProfile(
      profileName,
      cleanUrl,
      this.normalizedReferer(),
      this.videoQuality
    ));
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

  protected setProxyCapability(profile: ChromeProfileSummary, capability: 'youtube' | 'pornhub', value: boolean): void {
    const youtube = capability === 'youtube' ? value : null;
    const pornhub = capability === 'pornhub' ? value : null;
    this.busyProxyCapabilityName = profile.name;
    this.profilesError = false;
    this.profilesMessage = `Saving ${profile.name} proxy capability...`;
    this.dashboardService.updateChromeProfileProxyCapability(profile.name, youtube, pornhub).subscribe({
      next: (update) => {
        this.applyProxyCapabilityUpdate(update);
        this.clampProfileCount();
        this.profilesMessage = update.message || `${profile.name} proxy capability updated.`;
        this.profilesError = false;
        this.busyProxyCapabilityName = '';
      },
      error: (error) => {
        this.profilesMessage = this.errorMessage(error, `Could not update ${profile.name} proxy capability.`);
        this.profilesError = true;
        this.busyProxyCapabilityName = '';
      }
    });
  }

  private applyProxyCapabilityUpdate(update: { proxyKey?: string; supportsYoutube?: boolean | string; supportsPornhub?: boolean | string }): void {
    if (!this.profilesStatus?.profiles?.length || !update.proxyKey) {
      return;
    }
    this.profilesStatus = {
      ...this.profilesStatus,
      profiles: this.profilesStatus.profiles.map((profile) => {
        if (profile.proxyKey !== update.proxyKey) {
          return profile;
        }
        return {
          ...profile,
          supportsYoutube: update.supportsYoutube ?? profile.supportsYoutube,
          supportsPornhub: update.supportsPornhub ?? profile.supportsPornhub
        };
      })
    };
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

  protected supportsYoutube(profile: { supportsYoutube?: boolean | string }): boolean {
    return profile.supportsYoutube === true || profile.supportsYoutube === 'true';
  }

  protected supportsPornhub(profile: { supportsPornhub?: boolean | string }): boolean {
    return profile.supportsPornhub === true || profile.supportsPornhub === 'true';
  }

  protected blocksYoutube(profile: { supportsYoutube?: boolean | string }): boolean {
    return profile.supportsYoutube === false || profile.supportsYoutube === 'false';
  }

  protected blocksPornhub(profile: { supportsPornhub?: boolean | string }): boolean {
    return profile.supportsPornhub === false || profile.supportsPornhub === 'false';
  }

  protected proxyCapabilityLabel(profile: { supportsYoutube?: boolean | string; supportsPornhub?: boolean | string }): string {
    const youtube = this.supportsYoutube(profile);
    const pornhub = this.supportsPornhub(profile);
    const youtubeBlocked = this.blocksYoutube(profile);
    const pornhubBlocked = this.blocksPornhub(profile);
    if (youtube && pornhub) {
      return 'YT + PH';
    }
    if (youtube) {
      return 'YT only';
    }
    if (pornhub) {
      return 'PH only';
    }
    if (!youtubeBlocked || !pornhubBlocked) {
      return 'unknown';
    }
    return 'blocked';
  }

  protected capabilityShort(value?: boolean | string): string {
    if (value === true || value === 'true') {
      return 'ok';
    }
    if (value === false || value === 'false') {
      return 'no';
    }
    return '?';
  }

  protected capabilitySelectValue(value?: boolean | string): string {
    if (value === true || value === 'true') {
      return 'true';
    }
    if (value === false || value === 'false') {
      return 'false';
    }
    return '';
  }

  protected filteredProfiles(): ChromeProfileSummary[] {
    const profiles = this.modeEligibleProfiles();
    if (this.profileFilter === 'running') {
      return profiles.filter((profile) => this.isRunning(profile));
    }
    if (this.profileFilter === 'login') {
      return profiles.filter((profile) => !this.isLoggedIn(profile));
    }
    if (this.profileFilter === 'proxy') {
      return profiles.filter((profile) => this.blocksYoutube(profile) || this.blocksPornhub(profile));
    }
    return profiles;
  }

  protected toggleProfileDetails(profileName: string): void {
    this.expandedProfileName = this.expandedProfileName === profileName ? '' : profileName;
  }

  protected compactFeedback(value: string): string {
    const trimmed = value.trim();
    if (!trimmed) {
      return '';
    }
    const firstLine = trimmed.split(/\r?\n/).find(Boolean) || trimmed;
    return firstLine.length > 180 ? `${firstLine.slice(0, 177)}...` : firstLine;
  }

  protected applyLaunchMode(): void {
    this.requireYoutubeProxy = this.launchMode === 'youtube';
    this.requirePornhubProxy = this.launchMode === 'pornhub';
    this.clampProfileCount();
    this.saveLaunchSettings();
  }

  private syncLaunchModeFromFlags(): void {
    if (this.launchMode === 'pornhub' || (this.requirePornhubProxy && !this.requireYoutubeProxy)) {
      this.launchMode = 'pornhub';
      this.requireYoutubeProxy = false;
      this.requirePornhubProxy = true;
      return;
    }
    this.launchMode = 'youtube';
    this.requireYoutubeProxy = true;
    this.requirePornhubProxy = false;
  }

  protected launchFilterLabel(): string {
    if (this.requireYoutubeProxy && this.requirePornhubProxy) {
      return 'Exclude proxies blocked for YT or PH';
    }
    if (this.requireYoutubeProxy) {
      return 'Exclude YouTube-blocked proxies';
    }
    if (this.requirePornhubProxy) {
      return 'Exclude Pornhub-blocked proxies';
    }
    return 'No proxy capability filter';
  }

  protected isRunning(profile: { running?: boolean | string }): boolean {
    return profile.running === true || profile.running === 'true';
  }

  protected isBulkOk(status: string): boolean {
    return ['open_queued', 'restart_queued', 'closed'].includes(status);
  }

  protected isBulkSkip(status: string): boolean {
    return ['already_running', 'not_running'].includes(status);
  }

  protected checkResultFor(profileName: string) {
    if (!this.urlCheckStatus || this.urlCheckStatus.url !== this.normalizedUrl()) {
      return null;
    }
    return this.urlCheckStatus.results.find((result) => result.name === profileName) ?? null;
  }

  protected checkCompletedCount(): number {
    if (!this.urlCheckStatus || this.urlCheckStatus.url !== this.normalizedUrl()) {
      return 0;
    }
    return this.urlCheckStatus.completedCount ?? this.urlCheckStatus.results.filter((result) => result.reason !== 'Pending').length;
  }

  protected checkTotalCount(): number {
    if (!this.urlCheckStatus || this.urlCheckStatus.url !== this.normalizedUrl()) {
      return 0;
    }
    return this.urlCheckStatus.totalCount || this.urlCheckStatus.results.length;
  }

  protected checkProgressPercent(): number {
    const totalCount = this.checkTotalCount();
    if (!totalCount) {
      return 0;
    }
    return Math.max(0, Math.min(100, Math.round((this.checkCompletedCount() / totalCount) * 100)));
  }

  protected bulkResultFor(profileName: string) {
    return this.profilesStatus?.profileResults?.find((result) => result.name === profileName) ?? null;
  }

  private startBulkProgress(profileNames: string[], action: 'open' | 'restart' | 'close', actionLabel: string, attempt: number): void {
    this.stopBulkProgress();
    this.bulkProgressTimer = setTimeout(() => {
      this.dashboardService.getChromeProfilesStatus().subscribe({
        next: (status) => {
          this.profilesStatus = {
            ...status,
            profileResults: this.profilesStatus?.profileResults
          };
          const completeCount = this.bulkProgressCount(profileNames, action, status);
          this.message = `${actionLabel} checked profile(s)... Progress: ${completeCount}/${profileNames.length}.`;
          if (completeCount >= profileNames.length || attempt >= 30) {
            this.stopBulkProgress();
            return;
          }
          this.startBulkProgress(profileNames, action, actionLabel, attempt + 1);
        },
        error: () => this.stopBulkProgress()
      });
    }, 2000);
  }

  private stopBulkProgress(): void {
    if (this.bulkProgressTimer) {
      clearTimeout(this.bulkProgressTimer);
      this.bulkProgressTimer = null;
    }
  }

  private bulkProgressCount(profileNames: string[], action: 'open' | 'restart' | 'close', status: ChromeProfilesStatus): number {
    const profiles = status.profiles ?? [];
    return profileNames.filter((name) => {
      const profile = profiles.find((candidate) => candidate.name === name);
      const running = profile ? this.isRunning(profile) : false;
      return action === 'close' ? !running : running;
    }).length;
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

  protected profileGeo(profile: { proxyCountry?: string; proxyCity?: string; timezone?: string; language?: string; windowSize?: string }): string {
    const location = [profile.proxyCity?.trim(), profile.proxyCountry?.trim()].filter(Boolean).join(', ');
    const details = [profile.timezone?.trim(), profile.language?.trim(), profile.windowSize?.trim()].filter(Boolean).join(' | ');
    return [location, details].filter(Boolean).join(' | ');
  }

  private startCheckProgress(): void {
    this.stopCheckProgress();
    this.checkProgressTimer = setTimeout(() => {
      this.dashboardService.getChromeProfilesUrlCheckStatus().subscribe({
        next: (status) => {
          this.applyUrlCheckStatus(status);
          if (status.checking) {
            this.startCheckProgress();
          }
        },
        error: (error) => {
          this.checkMessage = this.errorMessage(error, 'Could not read URL check progress.');
          this.checkError = true;
          this.checkBusy = false;
          this.stopCheckProgress();
        }
      });
    }, 1000);
  }

  private stopCheckProgress(): void {
    if (this.checkProgressTimer) {
      clearTimeout(this.checkProgressTimer);
      this.checkProgressTimer = null;
    }
  }

  private applyUrlCheckStatus(status: ChromeProfilesUrlCheckStatus): void {
    this.urlCheckStatus = status;
    const completedCount = status.completedCount ?? status.results.filter((result) => result.reason !== 'Pending').length;
    if (status.checking) {
      this.checkMessage = `Checking URL: ${completedCount}/${status.totalCount}, OK ${status.okCount}.`;
      this.checkError = false;
      this.checkBusy = true;
      return;
    }
    this.checkMessage = `URL check: ${status.okCount}/${status.totalCount} profile(s) OK.`;
    this.checkError = status.okCount === 0;
    this.checkBusy = false;
    this.stopCheckProgress();
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
    return Math.max(1, this.launchEligibleProfiles().length || this.profilesStatus?.profiles?.length || 1);
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

  private launchEligibleProfiles(): ChromeProfileSummary[] {
    return this.modeEligibleProfiles();
  }

  private modeEligibleProfiles(): ChromeProfileSummary[] {
    const profiles = this.profilesStatus?.profiles ?? [];
    return profiles.filter((profile) =>
      (this.launchMode === 'youtube' && !this.blocksYoutube(profile)) ||
      (this.launchMode === 'pornhub' && !this.blocksPornhub(profile))
    );
  }

  protected normalizedUrl(): string {
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

  private normalizedReferer(): string {
    return this.refererHeader.trim();
  }

  private launchOptions(includeProxyFilters = false): { referer: string; videoQuality: string; requireYoutube?: boolean; requirePornhub?: boolean } {
    this.saveLaunchSettings();
    return {
      referer: this.normalizedReferer(),
      videoQuality: this.videoQuality,
      ...(includeProxyFilters ? {
        requireYoutube: this.requireYoutubeProxy,
        requirePornhub: this.requirePornhubProxy
      } : {})
    };
  }

  private loadLaunchSettings(): void {
    try {
      const raw = window.localStorage.getItem(this.launchSettingsStorageKey);
      if (!raw) {
        return;
      }
      const settings = JSON.parse(raw) as {
        refererHeader?: string;
        videoQuality?: string;
        requireYoutubeProxy?: boolean;
        requirePornhubProxy?: boolean;
        launchMode?: 'youtube' | 'pornhub' | 'any';
        profilesMinDelay?: number;
        profilesMaxDelay?: number;
      };
      this.refererHeader = settings.refererHeader ?? '';
      this.requireYoutubeProxy = Boolean(settings.requireYoutubeProxy);
      this.requirePornhubProxy = Boolean(settings.requirePornhubProxy);
      this.launchMode = settings.launchMode === 'pornhub' ? 'pornhub' : 'youtube';
      this.profilesMinDelay = settings.profilesMinDelay ?? this.profilesMinDelay;
      this.profilesMaxDelay = settings.profilesMaxDelay ?? this.profilesMaxDelay;
      if (settings.videoQuality && this.videoQualityOptions.some((option) => option.value === settings.videoQuality)) {
        this.videoQuality = settings.videoQuality;
      }
    } catch {
      this.refererHeader = '';
      this.videoQuality = 'auto';
    }
  }

  private saveLaunchSettings(): void {
    try {
      window.localStorage.setItem(this.launchSettingsStorageKey, JSON.stringify({
        refererHeader: this.normalizedReferer(),
        videoQuality: this.videoQuality,
        requireYoutubeProxy: this.requireYoutubeProxy,
        requirePornhubProxy: this.requirePornhubProxy,
        launchMode: this.launchMode,
        profilesMinDelay: this.normalizedDelay(this.profilesMinDelay, 20),
        profilesMaxDelay: this.normalizedDelay(this.profilesMaxDelay, 90)
      }));
    } catch {
      // Local persistence is optional.
    }
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
