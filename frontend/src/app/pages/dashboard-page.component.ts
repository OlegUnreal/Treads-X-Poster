import { AsyncPipe, NgClass, NgFor, NgIf } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DashboardService } from '../services/dashboard.service';
import { AdminUiStateService } from '../services/admin-ui-state.service';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [AsyncPipe, FormsModule, NgClass, NgFor, NgIf],
  template: `
    <section class="page" *ngIf="ui.vm$ | async as vm">
      <header class="hero">
        <div>
          <p class="eyebrow">Behind The Smile</p>
          <h1>Overview</h1>
          <p class="lede">
            A simple snapshot of what is ready, what is running, and what needs your attention next.
          </p>
        </div>
        <div class="status-card" [ngClass]="vm.summary.postingStatus">
          <span>Automation</span>
          <strong>{{ vm.summary.postingStatus }}</strong>
          <small>{{ vm.summary.jobStatus?.running ? 'Scheduled job is active' : 'No recurring job right now' }}</small>
        </div>
      </header>

      <section class="stats-grid">
        <article class="stat">
          <span>Ready Queue</span>
          <strong>{{ vm.summary.queueReady }}</strong>
        </article>
        <article class="stat">
          <span>Threads Ready</span>
          <strong>{{ vm.summary.threadsReady }}</strong>
        </article>
        <article class="stat">
          <span>X Ready</span>
          <strong>{{ vm.summary.xReady }}</strong>
        </article>
        <article class="stat">
          <span>Last Daily State</span>
          <strong class="small">{{ compactSummary(vm.summary.lastDailyMessage, 'Ready posts are available for the next run.') }}</strong>
        </article>
      </section>

      <section class="grid">
        <article class="panel">
          <div class="panel-head">
            <h2>Quick Actions</h2>
            <p>Only the core actions live here.</p>
          </div>
          <div class="actions">
            <button type="button" (click)="runDailyNow()">Run Daily Now</button>
            <button type="button" (click)="generateMorePosts()">Generate More Posts</button>
            <button type="button" (click)="publishThreadNow()">Publish 1 Thread</button>
            <button type="button" (click)="publishXNow()">Publish 1 X Post</button>
          </div>
          <p class="feedback" *ngIf="ui.actionResult$ | async as result" [ngClass]="{ error: !result.success }">
            <strong>{{ result.command }}</strong>
            <span>{{ result.message }}</span>
          </p>
        </article>

        <article class="panel">
          <div class="panel-head">
            <h2>Current Accounts</h2>
            <p>So it is always clear where posts will be published from.</p>
          </div>
          <label class="account-select">
            <span>Active publishing profile</span>
            <select
              [ngModel]="vm.summary.publisherAccounts.activeAccountId"
              (ngModelChange)="switchAccount($event)"
            >
              <option
                *ngFor="let account of vm.summary.publisherAccounts.availableAccounts"
                [ngValue]="account.id"
              >
                {{ account.label }}
              </option>
            </select>
          </label>
          <dl class="signals account-signals">
            <div>
              <dt>Profile</dt>
              <dd>{{ vm.summary.publisherAccounts.activeAccountLabel }}</dd>
            </div>
            <div>
              <dt>X</dt>
              <dd>{{ vm.summary.publisherAccounts.xAccountLabel }}</dd>
              <small>{{ vm.summary.publisherAccounts.xModeLabel }}</small>
            </div>
            <div>
              <dt>Threads</dt>
              <dd>{{ vm.summary.publisherAccounts.threadsAccountLabel }}</dd>
            </div>
          </dl>
        </article>

        <article class="panel">
          <div class="panel-head">
            <h2>What Matters Now</h2>
            <p>A quieter summary instead of one huge control screen.</p>
          </div>
          <dl class="signals">
            <div>
              <dt>Queue</dt>
              <dd>{{ vm.summary.queueReady }} post(s) are ready to go.</dd>
            </div>
            <div>
              <dt>Threads</dt>
              <dd>{{ humanizeThreadsMessage(vm.summary.lastThreadsMessage) }}</dd>
            </div>
            <div>
              <dt>Automation Job</dt>
              <dd>{{ humanizeJobMessage(vm.summary.jobStatus?.lastRunMessage || '') }}</dd>
            </div>
          </dl>
        </article>
      </section>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .page { display: grid; gap: 24px; }
    .hero { display: flex; justify-content: space-between; gap: 24px; align-items: start; }
    .eyebrow {
      margin: 0 0 10px;
      text-transform: uppercase;
      letter-spacing: 0.14em;
      font: 700 12px/1.2 "Segoe UI", sans-serif;
      color: #8a5a24;
    }
    h1 { margin: 0; font-size: clamp(2.4rem, 5vw, 4.2rem); line-height: 0.95; }
    .lede { max-width: 680px; margin: 14px 0 0; color: #52606d; font: 500 17px/1.6 "Segoe UI", sans-serif; }
    .status-card, .stat, .panel {
      background: rgba(255,255,255,0.78);
      border: 1px solid rgba(31,41,51,0.08);
      border-radius: 24px;
      box-shadow: 0 24px 50px rgba(69,58,42,0.12);
    }
    .status-card { min-width: 240px; padding: 18px 20px; font-family: "Segoe UI", sans-serif; }
    .status-card span, .status-card small { color: #52606d; }
    .status-card strong { display: block; margin-top: 8px; font-size: 28px; text-transform: capitalize; }
    .status-card.running strong { color: #136f63; }
    .status-card.stopped strong { color: #9a3412; }
    .status-card.degraded strong { color: #92400e; }
    .stats-grid, .grid { display: grid; gap: 18px; }
    .stats-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
    .grid { grid-template-columns: 1fr 0.9fr 0.95fr; }
    .stat { padding: 20px; font-family: "Segoe UI", sans-serif; }
    .stat span, .signals dt {
      display: block;
      color: #52606d;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      margin-bottom: 10px;
    }
    .stat strong { font-size: 38px; line-height: 1; }
    .stat strong.small {
      font-size: 18px;
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 4;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .panel { padding: 24px; }
    .panel-head h2 { margin: 0; font-size: 28px; letter-spacing: -0.03em; }
    .panel-head p { margin: 8px 0 0; color: #52606d; font-family: "Segoe UI", sans-serif; }
    .actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 18px; }
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
    .feedback {
      margin: 18px 0 0;
      padding: 12px 14px;
      border-radius: 14px;
      background: rgba(15, 118, 110, 0.09);
      color: #0f5132;
      font: 600 14px/1.5 "Segoe UI", sans-serif;
      display: grid;
      gap: 4px;
    }
    .feedback.error { background: rgba(154, 52, 18, 0.09); color: #7c2d12; }
    .signals { margin: 18px 0 0; display: grid; gap: 16px; font-family: "Segoe UI", sans-serif; }
    .account-select {
      margin-top: 18px;
      display: grid;
      gap: 8px;
      font-family: "Segoe UI", sans-serif;
    }
    .account-select span {
      color: #52606d;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }
    .account-select select {
      width: 100%;
      border: 1px solid rgba(31,41,51,0.12);
      border-radius: 14px;
      padding: 12px 14px;
      background: rgba(255,255,255,0.92);
      color: #243b53;
      font: 700 14px/1.5 "Segoe UI", sans-serif;
    }
    .account-signals small {
      display: block;
      margin-top: 6px;
      color: #52606d;
      font: 500 13px/1.5 "Segoe UI", sans-serif;
    }
    .signals dd {
      margin: 0;
      color: #334e68;
      line-height: 1.6;
      overflow-wrap: anywhere;
    }
    @media (max-width: 1080px) {
      .hero { flex-direction: column; }
      .stats-grid, .grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    }
    @media (max-width: 760px) {
      .stats-grid, .grid, .actions { grid-template-columns: 1fr; }
    }
  `]
})
export class DashboardPageComponent {
  protected readonly ui = inject(AdminUiStateService);
  private readonly dashboardService = inject(DashboardService);

  protected compactSummary(message: string | null | undefined, fallback: string): string {
    const normalized = this.normalizeMessage(message);
    if (!normalized) {
      return fallback;
    }

    return normalized.length > 140 ? `${normalized.slice(0, 137)}...` : normalized;
  }

  protected humanizeThreadsMessage(message: string | null | undefined): string {
    const normalized = this.normalizeMessage(message);
    if (!normalized) {
      return 'No recent Threads activity yet.';
    }

    if (normalized.toLowerCase().includes('published to threads')) {
      return 'The latest Threads post was published successfully.';
    }

    return this.compactSummary(normalized, 'No recent Threads activity yet.');
  }

  protected humanizeJobMessage(message: string | null | undefined): string {
    const normalized = this.normalizeMessage(message);
    if (!normalized || normalized === 'Job has not run yet.') {
      return 'The automation job has not run yet.';
    }

    const lower = normalized.toLowerCase();
    if (lower.includes('published to threads') && lower.includes('published to x')) {
      return 'The last automation run published to both Threads and X.';
    }
    if (lower.includes('published to threads')) {
      return 'The last automation run published to Threads.';
    }
    if (lower.includes('published to x')) {
      return 'The last automation run published to X.';
    }
    if (lower.includes('skipping generation')) {
      return 'The queue is already healthy, so generation was skipped in the last run.';
    }
    if (lower.includes('failed')) {
      return this.compactSummary(normalized, 'The last automation run had a problem.');
    }

    return this.compactSummary(normalized, 'The last automation run completed.');
  }

  private normalizeMessage(message: string | null | undefined): string {
    return (message ?? '')
      .replace(/\{[^}]*\}/g, '')
      .replace(/https?:\/\/\S+/g, '')
      .replace(/[A-Z]:\\[^ ]+/g, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  protected runDailyNow(): void {
    this.dashboardService.runDaily().subscribe((result) => this.ui.pushActionResult(result));
  }

  protected generateMorePosts(): void {
    this.dashboardService.runAutoCreate().subscribe((result) => this.ui.pushActionResult(result));
  }

  protected switchAccount(accountId: string): void {
    this.dashboardService.switchActiveAccount(accountId).subscribe(() => {
      this.ui.pushActionResult({
        success: true,
        command: 'switch-account',
        message: `Active publishing account changed to ${accountId}.`
      });
    });
  }

  protected publishThreadNow(): void {
    this.dashboardService.publishThread().subscribe((result) => this.ui.pushActionResult(result));
  }

  protected publishXNow(): void {
    this.dashboardService.publishX().subscribe((result) => this.ui.pushActionResult(result));
  }
}
