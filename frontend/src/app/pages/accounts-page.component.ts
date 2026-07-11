import { AsyncPipe, NgClass, NgFor, NgIf } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, Observable, combineLatest, map, of, startWith, switchMap } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { catchError } from 'rxjs/operators';
import {
  AccountConfig,
  AccountConfigRequest,
  AccountWorkspaceAccount,
  AccountWorkspaceSummary,
  ActionResult,
  GeneratePromptResponse
} from '../models/dashboard.models';
import { AdminUiStateService } from '../services/admin-ui-state.service';
import { DashboardService } from '../services/dashboard.service';

type AccountProfileRow = {
  account: AccountWorkspaceAccount;
  platform: 'x' | 'threads';
  key: string;
};

@Component({
  selector: 'app-accounts-page',
  standalone: true,
  imports: [AsyncPipe, NgClass, NgFor, NgIf, FormsModule],
  template: `
    <section class="page" *ngIf="vm$ | async as workspace">
      <header class="page-head">
        <div>
          <p class="eyebrow">Accounts</p>
          <h1>Friends and channels</h1>
          <p class="lede">Each row is a separate X or Threads profile with its own queue and credentials.</p>
        </div>
        <div class="totals">
          <span>Ready</span>
          <strong>{{ workspace.totalReady }}</strong>
          <small>{{ workspace.totalFailed }} failed · {{ workspace.totalPublished }} published</small>
        </div>
      </header>

      <section class="layout">
        <aside class="account-rail">
          <div class="add-account-actions">
            <button class="new-account" type="button" (click)="newAccount('x')">
              + Add X account
            </button>
            <button class="new-account" type="button" (click)="newAccount('threads')">
              + Add Threads account
            </button>
          </div>
          <p class="onboarding-note" *ngIf="creatingAccount">
            New account: set name, add credentials, tune prompt, then save.
          </p>
          <div class="account-list" *ngIf="workspace.accounts.length > 0">
            <article
              class="account-row"
              *ngFor="let row of accountRows(workspace.accounts); trackBy: trackByAccountRow"
              [class.active]="row.key === selectedProfileKey"
            >
              <button
                type="button"
                class="account-row-main"
                (click)="selectAccount(row)"
              >
                <span class="initial">{{ accountInitial(row.account) }}</span>
                <span class="copy">
                  <strong>{{ displayPlatformName(row.account, row.platform) }}</strong>
                  <small>{{ row.account.id }} · {{ row.platform === 'x' ? 'X' : 'Threads' }}</small>
                  <small class="status-line">
                    {{ row.platform === 'x' ? 'X' : 'Threads' }}
                    {{ row.platform === 'x' ? row.account.xReady : row.account.threadsReady }}
                    /
                    {{ row.platform === 'x' ? row.account.xFailed : row.account.threadsFailed }}
                    <span
                      class="inline-pill"
                      [class.ready]="platformHealth(row.account, row.platform) === 'good'"
                      [class.warning]="platformHealth(row.account, row.platform) !== 'good'"
                    >
                      {{ row.platform === 'x' ? (row.account.xConfigured ? 'configured' : 'not connected') : (row.account.threadsConfigured ? 'configured' : 'not connected') }}
                    </span>
                  </small>
                </span>
              </button>
            </article>
          </div>
          <p class="empty-list" *ngIf="workspace.accounts.length === 0">No accounts yet.</p>
        </aside>

        <main class="workbench" *ngIf="selectedAccount(workspace) as account">
          <section class="account-header">
            <div>
              <h2>{{ selectedProfileTitle(account) }}</h2>
              <p>{{ selectedProfileSummary(account) }}</p>
              <small class="field-note" *ngIf="selectedProfileNeedsCredentials(account)">
                {{ selectedProfileNeedsCredentialsText(account) }}
              </small>
            </div>
              <button
                class="ghost"
                type="button"
                [disabled]="busyAction !== null || !account.id"
                (click)="makeActive(account)"
              >
              {{ workspace.activeAccountId === account.id ? 'Active account' : 'Make active' }}
            </button>
          </section>

          <section class="panel settings-panel">
            <div class="section-head">
              <h3>Account settings</h3>
              <span>{{ accountForm.source === 'env' ? 'From .env, editable copy' : 'Saved in app' }}</span>
            </div>
            <div class="settings-grid">
              <label>
                <span>Name</span>
                <input [(ngModel)]="accountForm.label" />
              </label>
              <label>
                <span>ID</span>
                <input [(ngModel)]="accountForm.id" [disabled]="accountForm.source === 'env'" />
              </label>
              <ng-container *ngIf="selectedProfilePlatform === 'x'">
                <div class="wide settings-divider">
                  <strong>X account</strong>
                  <small>Profile, credentials and generation settings for X.</small>
                </div>
                <label>
                  <span>X language</span>
                  <input [(ngModel)]="accountForm.xLanguage" />
                </label>
                <label>
                  <span>X default count</span>
                  <input type="number" min="1" max="24" [(ngModel)]="accountForm.xDefaultPostCount" />
                </label>
                <label>
                  <span>X handle</span>
                  <input [(ngModel)]="accountForm.xAccountLabel" placeholder="@friend_x" />
                </label>
                <label>
                  <span>X publish mode</span>
                  <select [(ngModel)]="accountForm.xPublishMode">
                    <option value="selenium">Browser / Selenium</option>
                    <option value="api">X API</option>
                    <option value="auto">API with browser fallback</option>
                  </select>
                </label>
                <label class="wide">
                  <span>X browser profile</span>
                  <input [(ngModel)]="accountForm.xBrowserProfileDir" />
                </label>
                <label>
                  <span>X access token</span>
                  <input [(ngModel)]="accountForm.xAccessToken" type="password" autocomplete="off" />
                </label>
                <label>
                  <span>X API key</span>
                  <input [(ngModel)]="accountForm.xApiKey" type="password" autocomplete="off" />
                </label>
              </ng-container>

              <ng-container *ngIf="selectedProfilePlatform === 'threads'">
                <div class="wide settings-divider">
                  <strong>Threads account</strong>
                  <small>Profile, credentials and generation settings for Threads.</small>
                </div>
                <label>
                  <span>Threads language</span>
                  <input [(ngModel)]="accountForm.threadsLanguage" />
                </label>
                <label>
                  <span>Threads default count</span>
                  <input type="number" min="1" max="24" [(ngModel)]="accountForm.threadsDefaultPostCount" />
                </label>
                <label>
                  <span>Threads nickname</span>
                  <input [(ngModel)]="accountForm.threadsAccountLabel" placeholder="@friend_threads" />
                </label>
                <label>
                  <span>Threads user ID</span>
                  <input [(ngModel)]="accountForm.threadsUserId" />
                </label>
                <label class="wide">
                  <span>Threads access token</span>
                  <input [(ngModel)]="accountForm.threadsAccessToken" type="password" autocomplete="off" />
                </label>
                <div class="wide inline-actions">
                  <button
                    class="ghost"
                    type="button"
                    [disabled]="busyAction !== null"
                    (click)="lookupThreadsNickname()"
                  >
                    {{ busyAction === 'threads-lookup' ? 'Fetching...' : 'Fetch Threads nickname' }}
                  </button>
                  <small>Uses Threads user ID and access token.</small>
                </div>
              </ng-container>
            </div>

            <div class="actions">
              <button
                type="button"
                [disabled]="busyAction !== null || !canSaveAccount()"
                (click)="saveAccountSettings()"
              >
                {{ busyAction === 'save-account' ? 'Saving...' : 'Save account' }}
              </button>
              <button class="ghost" type="button" (click)="newAccount(selectedProfilePlatform)">New</button>
              <button
                class="danger"
                type="button"
                *ngIf="accountForm.source === 'ui'"
                [disabled]="busyAction !== null"
                (click)="deleteAccountSettings()"
              >
                Delete
              </button>
            </div>
            <small class="field-note" *ngIf="!canSaveAccount()">Set a name before saving.</small>
          </section>

          <section class="composer-grid">
            <article class="panel prompt-panel">
              <div class="section-head">
                <h3>{{ selectedProfilePlatform === 'x' ? 'X prompt' : 'Threads prompt' }}</h3>
                <span>Saved for this publishing profile</span>
              </div>
              <textarea [(ngModel)]="accountPrompt" rows="9"></textarea>
              <small class="field-note" *ngIf="!selectedProfileConfigured(account)">
                Configure this profile before publishing. You can still generate drafts.
              </small>
              <div class="count-field-wrap">
                <label class="count-field">
                  <span>Count</span>
                  <input type="number" min="1" max="12" [(ngModel)]="postCount" />
                </label>
              </div>
              <small class="field-note">
                Generating to:
                {{ selectedProfilePlatform === 'x' ? 'X' : 'Threads' }} only
              </small>
              <div class="actions">
                <button
                  type="button"
                  [disabled]="busyAction !== null || !canGenerate(account)"
                  (click)="generateForAccount(account)"
                >
                  {{ busyAction === 'generate' ? 'Generating...' : 'Generate into queue' }}
                </button>
                <button class="ghost" type="button" [disabled]="busyAction !== null" (click)="savePrompt(account)">
                  Save prompt
                </button>
              </div>
              <small class="field-note" *ngIf="!canGenerate(account)">
                {{ generationValidationMessage(account) }}
              </small>
            </article>

            <article class="panel media-panel">
              <div class="section-head">
                <h3>Photos</h3>
                <span>{{ selectedPhotoFiles.length || account.mediaAttached }} attached</span>
              </div>
              <label class="dropzone">
                <input type="file" accept="image/*" multiple (change)="selectPhotos($event)" />
                <strong>{{ selectedPhotoFiles.length ? selectedPhotoFiles.length + ' photo(s) selected' : 'Choose photos' }}</strong>
                <small>Use photos as post material, or keep generating text-only posts.</small>
              </label>
              <div class="media-modes">
                <label>
                  <input type="radio" name="mediaMode" value="mixed" [(ngModel)]="mediaMode" />
                  <span>Mixed</span>
                </label>
                <label>
                  <input type="radio" name="mediaMode" value="photo" [(ngModel)]="mediaMode" />
                  <span>Photo first</span>
                </label>
                <label>
                  <input type="radio" name="mediaMode" value="text" [(ngModel)]="mediaMode" />
                  <span>Text only</span>
                </label>
              </div>
              <button
                type="button"
                [disabled]="busyAction !== null || !canCreateFromPhotos(account) || selectedPhotoFiles.length === 0"
                (click)="createFromPhotos(account)"
              >
                {{ busyAction === 'photo-batch' ? 'Creating...' : 'Create posts from photos' }}
              </button>
              <small class="field-note" *ngIf="!canCreateFromPhotos(account) || selectedPhotoFiles.length === 0">
                {{ photoGenerationValidationMessage(account) }}
              </small>
            </article>
          </section>

      <section class="queue-band">
            <article>
              <span>Text-only</span>
              <strong>{{ account.textOnly }}</strong>
            </article>
            <article>
              <span>With photos</span>
              <strong>{{ account.mediaAttached }}</strong>
            </article>
            <article>
              <span>Failed</span>
              <strong>{{ account.xFailed + account.threadsFailed }}</strong>
            </article>
            <article>
              <span>Published</span>
              <strong>{{ account.published }}</strong>
            </article>
          </section>

          <p class="feedback" *ngIf="ui.actionResult$ | async as result" [class.error]="!result.success">
            <strong>{{ result.command }}</strong>
            <span>{{ result.message }}</span>
          </p>
          <ol class="generated-list" *ngIf="generatedPosts.length > 0">
            <li *ngFor="let post of generatedPosts">{{ post }}</li>
          </ol>
        </main>
      </section>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .page { display: grid; gap: 16px; }
    .page-head {
      display: flex;
      justify-content: space-between;
      gap: 18px;
      align-items: end;
    }
    .eyebrow {
      margin: 0 0 6px;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: #64748b;
      font: 800 11px/1.2 "Segoe UI", sans-serif;
    }
    h1, h2, h3 { margin: 0; color: #17212b; }
    h1 { font-size: 30px; line-height: 1.05; }
    h2 { font-size: 22px; }
    h3 { font-size: 16px; }
    .lede { margin: 7px 0 0; color: #52606d; font: 500 14px/1.45 "Segoe UI", sans-serif; }
    .totals, .panel, .account-rail, .account-header, .queue-band article {
      background: #ffffff;
      border: 1px solid #dde3ea;
      border-radius: 8px;
      box-shadow: 0 8px 22px rgba(15, 23, 42, 0.05);
    }
    .totals {
      min-width: 150px;
      padding: 12px 14px;
      display: grid;
      gap: 2px;
      font-family: "Segoe UI", sans-serif;
    }
    .totals span, .totals small, .section-head span, .queue-band span, .field-note {
      color: #64748b;
      font: 700 12px/1.25 "Segoe UI", sans-serif;
    }
    .totals strong { font-size: 28px; line-height: 1; }
    .layout {
      display: grid;
      grid-template-columns: 280px minmax(0, 1fr);
      gap: 12px;
      align-items: start;
    }
    .account-rail {
      padding: 8px;
      display: grid;
      gap: 6px;
      position: sticky;
      top: 12px;
    }
    .add-account-actions {
      display: grid;
      grid-template-columns: 1fr;
      gap: 6px;
    }
    .new-account {
      width: 100%;
      margin-bottom: 4px;
      background: #0f766e;
      color: white;
    }
    .onboarding-note {
      margin: 0 3px 6px;
      color: #64748b;
      font: 700 12px/1.4 "Segoe UI", sans-serif;
    }
    .account-row {
      width: 100%;
      border: 1px solid transparent;
      border-radius: 8px;
      background: #f8fafc;
      color: #17212b;
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: 9px;
      align-items: center;
      padding: 9px;
      text-align: left;
    }
    .account-row.active {
      border-color: #2563eb;
      background: #edf5ff;
    }
    .account-row-main {
      width: 100%;
      border: 0;
      margin: 0;
      padding: 0;
      display: grid;
      grid-template-columns: 34px minmax(0, 1fr);
      gap: 9px;
      align-items: center;
      background: transparent;
      color: inherit;
      cursor: pointer;
    }
    .status-line {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 6px;
    }
    .inline-pill {
      border-radius: 999px;
      padding: 2px 8px;
      text-transform: uppercase;
      font: 700 10px/1 "Segoe UI", sans-serif;
      letter-spacing: 0.03em;
      color: #92400e;
      background: #fef3c7;
    }
    .inline-pill.ready { color: #166534; background: #dcfce7; }
    .inline-pill.warning { color: #9f1239; background: #fee2e2; }
    .empty-list {
      margin: 0;
      color: #64748b;
      font: 600 12px/1.4 "Segoe UI", sans-serif;
    }
    .initial {
      width: 34px;
      height: 34px;
      display: grid;
      place-items: center;
      border-radius: 50%;
      background: #17212b;
      color: white;
      font: 800 13px/1 "Segoe UI", sans-serif;
    }
    .copy { min-width: 0; display: grid; gap: 2px; }
    .copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font: 800 13px/1.2 "Segoe UI", sans-serif; }
    .copy small { color: #64748b; font: 700 12px/1.2 "Segoe UI", sans-serif; }
    .workbench { display: grid; gap: 12px; }
    .account-header {
      padding: 14px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
    }
    .account-header p { margin: 5px 0 0; color: #52606d; font: 600 13px/1.4 "Segoe UI", sans-serif; }
    .field-note { display: block; margin-top: 2px; }
    .composer-grid, .queue-band { display: grid; gap: 10px; }
    .composer-grid { grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr); align-items: stretch; }
    .panel { padding: 14px; display: grid; gap: 10px; }
    .section-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
    .settings-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 10px;
    }
    .settings-grid label { display: grid; gap: 5px; min-width: 0; }
    .settings-grid label span {
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      font: 800 11px/1.2 "Segoe UI", sans-serif;
    }
    .settings-grid .wide { grid-column: 1 / -1; }
    .settings-divider {
      display: grid;
      gap: 2px;
      margin-top: 6px;
      padding-top: 12px;
      border-top: 1px solid #e2e8f0;
    }
    .settings-divider strong {
      color: #17212b;
      font: 900 13px/1.2 "Segoe UI", sans-serif;
    }
    .settings-divider small {
      color: #64748b;
      font: 700 12px/1.35 "Segoe UI", sans-serif;
    }
    .inline-actions {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }
    .inline-actions small {
      color: #64748b;
      font: 700 12px/1.25 "Segoe UI", sans-serif;
    }
    textarea, input[type="number"], .settings-grid input, .settings-grid select {
      width: 100%;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      padding: 10px 11px;
      background: #ffffff;
      color: #17212b;
      font: 500 14px/1.45 "Segoe UI", sans-serif;
    }
    .settings-grid input:disabled {
      background: #f1f5f9;
      color: #64748b;
    }
    textarea { resize: vertical; }
    .media-modes {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
      align-items: center;
    }
    .media-modes label {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      color: #334155;
      font: 800 13px/1.2 "Segoe UI", sans-serif;
    }
    .count-field-wrap {
      display: flex;
      align-items: center;
    }
    .count-field {
      margin: 0;
      display: grid !important;
      grid-template-columns: auto 70px;
    }
    .dropzone {
      min-height: 124px;
      border: 1px dashed #94a3b8;
      border-radius: 8px;
      background: #f8fafc;
      display: grid;
      place-items: center;
      align-content: center;
      gap: 5px;
      padding: 16px;
      text-align: center;
      cursor: pointer;
    }
    .dropzone input { display: none; }
    .dropzone strong { color: #17212b; font: 800 14px/1.25 "Segoe UI", sans-serif; }
    .dropzone small { color: #64748b; font: 600 12px/1.4 "Segoe UI", sans-serif; }
    .actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
    button {
      border: 0;
      border-radius: 8px;
      padding: 9px 12px;
      background: #2563eb;
      color: white;
      cursor: pointer;
      font: 800 13px/1.2 "Segoe UI", sans-serif;
    }
    button.ghost {
      background: #ffffff;
      color: #334155;
      border: 1px solid #cbd5e1;
    }
    button.danger { background: #be123c; }
    button:disabled { opacity: 0.62; cursor: wait; }
    .queue-band { grid-template-columns: repeat(4, minmax(0, 1fr)); }
    .queue-band article { padding: 12px; display: grid; gap: 5px; }
    .queue-band strong { font: 900 24px/1 "Segoe UI", sans-serif; }
    .feedback {
      margin: 0;
      padding: 10px 12px;
      border-radius: 8px;
      background: #e9f7ef;
      color: #146c43;
      font: 600 13px/1.35 "Segoe UI", sans-serif;
      display: grid;
      gap: 2px;
    }
    .feedback.error { background: #fff1f2; color: #be123c; }
    .generated-list {
      margin: 0;
      padding: 12px 12px 12px 30px;
      border: 1px solid #dde3ea;
      border-radius: 8px;
      background: #ffffff;
      color: #334155;
      font: 500 13px/1.5 "Segoe UI", sans-serif;
    }
    @media (max-width: 980px) {
      .layout,
      .composer-grid,
      .queue-band {
        grid-template-columns: 1fr;
      }
      .account-rail { position: static; }
    }
    @media (max-width: 680px) {
      .page-head, .account-header { display: grid; align-items: stretch; }
      .settings-grid { grid-template-columns: 1fr; }
      .totals, .account-header button, .actions button { width: 100%; }
      .actions { justify-content: stretch; }
    }
  `]
})
export class AccountsPageComponent {
  protected readonly ui = inject(AdminUiStateService);
  private readonly dashboardService = inject(DashboardService);
  private readonly refreshTick = new BehaviorSubject<void>(undefined);
  protected readonly vm$ = combineLatest([
    this.ui.queuePlatform$.pipe(startWith('x' as const)),
    this.refreshTick
  ]).pipe(
    switchMap(() => combineLatest([
      this.dashboardService.getAccountWorkspace(),
      this.dashboardService.getAccountConfigs()
    ])),
    map(([workspace, configs]) => {
      this.accountConfigs = configs;
      return workspace;
    }),
    catchError((error) => {
      this.ui.pushActionResult({
        success: false,
        command: 'accounts-load',
        message: error?.error?.message || error?.message || 'Failed to load accounts configuration.'
      });
      return of({
        activeAccountId: '',
        totalReady: 0,
        totalFailed: 0,
        totalPublished: 0,
        accounts: []
      } as AccountWorkspaceSummary);
    })
  );

  protected accountConfigs: AccountConfig[] = [];
  protected creatingAccount = false;
  protected selectedProfileKey = '';
  protected selectedProfilePlatform: 'x' | 'threads' = 'x';
  protected accountPrompt = '';
  protected postCount = 4;
  protected mediaMode: 'mixed' | 'photo' | 'text' = 'mixed';
  protected selectedPhotoFiles: File[] = [];
  protected generatedPosts: string[] = [];
  protected busyAction: string | null = null;
  protected accountForm: AccountConfigRequest & { source?: string } = this.blankAccountForm();

  protected accountRows(accounts: AccountWorkspaceAccount[]): AccountProfileRow[] {
    return accounts.flatMap((account) => {
      const profileRows: AccountProfileRow[] = [];
      const explicitPlatform = this.platformFromAccountId(account.id);
      if (explicitPlatform) {
        profileRows.push({ account, platform: explicitPlatform, key: `${account.id}:${explicitPlatform}` });
        return profileRows;
      }

      if (account.xConfigured || account.threadsConfigured || account.xAccountLabel !== 'X account is not configured') {
        profileRows.push({ account, platform: 'x', key: `${account.id}:x` });
      }
      if (account.threadsConfigured || account.threadsAccountLabel !== 'Threads account is not configured') {
        profileRows.push({ account, platform: 'threads', key: `${account.id}:threads` });
      }
      return profileRows;
    });
  }

  protected trackByAccountRow(_index: number, row: AccountProfileRow): string {
    return row.key;
  }

  private platformFromAccountId(accountId: string): 'x' | 'threads' | null {
    const normalized = (accountId ?? '').toLowerCase();
    if (normalized.endsWith('-x')) {
      return 'x';
    }
    if (normalized.endsWith('-threads')) {
      return 'threads';
    }
    return null;
  }

  protected selectedAccount(workspace: AccountWorkspaceSummary): AccountWorkspaceAccount | null {
    if (this.creatingAccount) {
      return this.draftWorkspaceAccount();
    }

    const rows = this.accountRows(workspace.accounts);
    const selected = rows.find((row) => row.key === this.selectedProfileKey)?.account ?? rows[0]?.account ?? null;
    const selectedProfile = rows.find((row) => row.key === this.selectedProfileKey);
    if (selected && (!this.selectedProfileKey || !selectedProfile)) {
      const firstRow = rows[0];
      if (firstRow) {
        this.selectedProfileKey = firstRow.key;
        this.selectedProfilePlatform = firstRow.platform;
        this.loadAccountForm(firstRow.account);
      }
    }

    if (selectedProfile) {
      this.selectedProfilePlatform = selectedProfile.platform;
    }
    return selected;
  }

  protected selectAccount(row: AccountProfileRow): void {
    if (this.selectedProfileKey === row.key) {
      return;
    }
    this.applySelectedProfileContent();
    this.creatingAccount = false;
    this.selectedProfileKey = row.key;
    this.selectedProfilePlatform = row.platform;
    this.generatedPosts = [];
    this.loadAccountForm(row.account);
  }

  protected accountInitial(account: AccountWorkspaceAccount): string {
    return (account.label || account.id || '?').trim().slice(0, 1).toUpperCase();
  }

  protected platformHealth(account: AccountWorkspaceAccount, platform: 'x' | 'threads'): 'good' | 'warning' | 'blocked' {
    if (platform === 'x') {
      if (account.xFailed > 0) {
        return 'blocked';
      }
      return account.xConfigured ? 'good' : 'warning';
    }
    if (account.threadsFailed > 0) {
      return 'blocked';
    }
    return account.threadsConfigured ? 'good' : 'warning';
  }

  protected displayPlatformName(account: AccountWorkspaceAccount, platform: 'x' | 'threads'): string {
    return platform === 'x'
      ? (account.xAccountLabel || 'X account is not configured')
      : (account.threadsAccountLabel || 'Threads account is not configured');
  }

  protected selectedProfileTitle(account: AccountWorkspaceAccount): string {
    const platformName = this.selectedProfilePlatform === 'x' ? 'X' : 'Threads';
    return `${this.displayPlatformName(account, this.selectedProfilePlatform)} · ${platformName}`;
  }

  protected selectedProfileSummary(account: AccountWorkspaceAccount): string {
    const selectedProfileReady = this.selectedProfilePlatform === 'x'
      ? account.xReady
      : account.threadsReady;
    const selectedProfileFailed = this.selectedProfilePlatform === 'x'
      ? account.xFailed
      : account.threadsFailed;
    const selectedProfileConnected = this.selectedProfilePlatform === 'x'
      ? account.xConfigured
      : account.threadsConfigured;
    return selectedProfileConnected
      ? `${this.selectedProfilePlatform === 'x' ? 'X' : 'Threads'} connected. ${selectedProfileReady} ready, ${selectedProfileFailed} failed.`
      : 'Selected platform is not connected yet. You can still prepare prompts and queues.';
  }

  protected selectedProfileConfigured(account: AccountWorkspaceAccount): boolean {
    if (this.selectedProfilePlatform === 'x') {
      return (
        account.xConfigured ||
        !!this.accountForm.xAccessToken?.trim() ||
        !!this.accountForm.xApiKey?.trim() ||
        !!this.accountForm.xClientId?.trim() ||
        !!this.accountForm.xClientSecret?.trim() ||
        !!this.accountForm.xAccountLabel?.trim()
      );
    }
    return (
      account.threadsConfigured ||
      !!this.accountForm.threadsAccessToken?.trim() ||
      !!this.accountForm.threadsUserId?.trim() ||
      !!this.accountForm.threadsAppId?.trim() ||
      !!this.accountForm.threadsAppSecret?.trim() ||
      !!this.accountForm.threadsAccountLabel?.trim()
    );
  }

  protected selectedProfileNeedsCredentials(account: AccountWorkspaceAccount): boolean {
    return !this.selectedProfileConfigured(account);
  }

  protected selectedProfileNeedsCredentialsText(account: AccountWorkspaceAccount): string {
    if (this.selectedProfilePlatform === 'x') {
      return 'X profile is not connected yet.';
    }
    return 'Threads profile is not connected yet.';
  }

  protected savePrompt(account: AccountWorkspaceAccount): void {
    this.applySelectedProfileContent();
    this.saveAccountSettings();
  }

  protected saveAccountSettings(): void {
    if (!this.canSaveAccount()) {
      this.ui.pushActionResult({
        success: false,
        command: 'save-account',
        message: 'Set account name before saving.'
      });
      return;
    }

    this.applySelectedProfileContent();
    const request = this.cleanAccountRequest({
      ...this.accountForm,
      prompt: this.selectedProfilePrompt()
    }, true);
    const requestedId = request.id;
    this.runAction('save-account', this.dashboardService.saveAccountConfig(request), (result: AccountConfig) => {
      this.accountForm = this.toForm(result);
      const selectedProfile = `${result.id}:${this.selectedProfilePlatform}`;
      this.selectedProfileKey = selectedProfile;
      this.creatingAccount = false;
      this.accountPrompt = this.selectedProfilePromptFromResult(result);
      const idNotice = requestedId === result.id
        ? ''
        : ` ID assigned: ${result.id}.`;
      this.ui.pushActionResult({
        success: true,
        command: 'save-account',
        message: `${result.label} saved.${idNotice}`
      });
    });
  }

  protected canSaveAccount(): boolean {
    return !!this.accountForm.label?.trim();
  }

  protected lookupThreadsNickname(): void {
    if (!this.accountForm.threadsUserId?.trim() || !this.accountForm.threadsAccessToken?.trim()) {
      this.ui.pushActionResult({
        success: false,
        command: 'threads-lookup',
        message: 'Add Threads user ID and access token first.'
      });
      return;
    }
    const request = this.cleanAccountRequest({ ...this.accountForm, prompt: this.selectedProfilePrompt() }, false);
    this.runAction('threads-lookup', this.dashboardService.lookupThreadsProfile(request), (result) => {
      this.accountForm.threadsAccountLabel = result.label;
      this.ui.pushActionResult({
        success: true,
        command: 'threads-lookup',
        message: `Found ${result.label}. Save the account to keep it.`
      });
    });
  }

  protected deleteAccountSettings(): void {
    if (!this.accountForm.id || !window.confirm(`Delete ${this.accountForm.label || this.accountForm.id}?`)) {
      return;
    }
    this.runAction('delete-account', this.dashboardService.deleteAccountConfig(this.accountForm.id), (result: ActionResult) => {
      this.selectedProfileKey = '';
      this.creatingAccount = false;
      this.accountForm = this.blankAccountForm();
      this.accountPrompt = this.accountForm.prompt;
      this.generatedPosts = [];
      this.ui.pushActionResult(result);
    });
  }

  protected newAccount(platform: 'x' | 'threads' = 'x'): void {
    this.creatingAccount = true;
    this.selectedProfileKey = '';
    this.selectedProfilePlatform = platform;
    this.accountForm = this.blankAccountForm();
    this.accountPrompt = this.selectedProfilePrompt();
    this.generatedPosts = [];
  }

  protected makeActive(account: AccountWorkspaceAccount): void {
    this.runAction('switch-account', this.dashboardService.switchActiveAccount(account.id), () => {
      this.ui.pushActionResult({
        success: true,
        command: 'switch-account',
        message: `${account.label} is now the active publishing account.`
      });
    });
  }

  protected canGenerate(account: AccountWorkspaceAccount): boolean {
    return this.generateValidation(account, false) === null;
  }

  protected generationValidationMessage(account: AccountWorkspaceAccount): string {
    return this.generateValidation(account, true) ?? '';
  }

  protected canCreateFromPhotos(account: AccountWorkspaceAccount): boolean {
    return this.photoValidation(account, false) === null;
  }

  protected photoGenerationValidationMessage(account: AccountWorkspaceAccount): string {
    if (this.selectedPhotoFiles.length === 0) {
      return 'Choose photos first.';
    }
    return this.photoValidation(account, true) ?? '';
  }

  protected generateForAccount(account: AccountWorkspaceAccount): void {
    const message = this.generateValidation(account, true);
    if (message) {
      this.ui.pushActionResult({
        success: false,
        command: 'generate',
        message
      });
      return;
    }
    const prompt = this.accountPrompt.trim();
    const targetProfiles = this.targetProfiles(account);
    this.savePromptSilently(account);
    this.runAction(
      'generate',
      this.dashboardService.generatePrompt({
        prompt,
        topic: `${account.label} account prompt`,
        language: this.selectedProfileLanguage(),
        count: this.postCount,
        platforms: [],
        accountIds: [],
        targetProfiles,
        saveToQueue: true
      }),
      (result: GeneratePromptResponse) => {
        this.generatedPosts = result.posts;
        this.ui.pushActionResult({
          success: true,
          command: 'generate',
          message: result.message
        });
      }
    );
  }

  protected selectPhotos(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedPhotoFiles = Array.from(input.files ?? []);
  }

  protected createFromPhotos(account: AccountWorkspaceAccount): void {
    const message = this.photoValidation(account, true);
    if (message) {
      this.ui.pushActionResult({
        success: false,
        command: 'photo-batch',
        message
      });
      return;
    }
    if (this.selectedPhotoFiles.length === 0) {
      this.ui.pushActionResult({
        success: false,
        command: 'photo-batch',
        message: 'Choose photos first.'
      });
      return;
    }
    this.savePromptSilently(account);
    const targetProfiles = this.targetProfiles(account);
    this.runAction(
      'photo-batch',
      this.dashboardService.createPhotoBatch({
        photos: this.selectedPhotoFiles,
        prompt: this.photoPrompt(),
        topic: `${account.label} uploaded photos`,
        language: this.selectedProfileLanguage(),
        platforms: [],
        accountIds: [],
        targetProfiles,
        publishNow: false
      }),
      (result: ActionResult) => {
        this.selectedPhotoFiles = result.success ? [] : this.selectedPhotoFiles;
        this.ui.pushActionResult(result);
      }
    );
  }

  private runAction<T>(command: string, request$: Observable<T>, onResult: (result: T) => void): void {
    if (this.busyAction !== null) {
      return;
    }
    this.busyAction = command;
    request$
      .pipe(finalize(() => (this.busyAction = null)))
      .subscribe({
        next: (result) => {
          onResult(result);
          this.refreshTick.next();
        },
        error: (error) => this.ui.pushActionResult({
          success: false,
          command,
          message: error?.error?.message || error?.message || 'Request failed.'
        })
      });
  }

  private generateValidation(account: AccountWorkspaceAccount, withMessage: boolean): string | null {
    if (!account.id) {
      return withMessage ? 'Save this account before generating its queue.' : null;
    }
    if (!this.accountPrompt.trim()) {
      return withMessage ? 'Add an account prompt first.' : null;
    }
    if (this.postCount < 1) {
      return withMessage ? 'Post count must be at least 1.' : null;
    }
    if (this.postCount > 12) {
      return withMessage ? 'Post count is capped at 12 in this workflow.' : null;
    }
    return null;
  }

  private photoValidation(account: AccountWorkspaceAccount, withMessage: boolean): string | null {
    if (!account.id) {
      return withMessage ? 'Save this account before creating its photo queue.' : null;
    }
    return null;
  }

  private targetProfiles(account: AccountWorkspaceAccount): string[] {
    const target = this.selectedProfileTarget();
    if (!target) {
      return [`${account.id}:${this.selectedProfilePlatform}`];
    }
    return [target];
  }

  private selectedProfileTarget(): string {
    if (!this.selectedProfileKey) {
      return '';
    }
    return this.selectedProfileKey.includes(':')
      ? this.selectedProfileKey
      : `${this.selectedProfileKey}:${this.selectedProfilePlatform}`;
  }

  private selectedProfileAccountId(): string {
    const target = this.selectedProfileTarget();
    if (!target || target.lastIndexOf(':') <= 0) {
      return '';
    }
    return target.slice(0, target.lastIndexOf(':'));
  }

  private photoPrompt(): string {
    const base = this.accountPrompt.trim();
    if (this.mediaMode === 'photo') {
      return `${base}\nUse the photo as the main post.`;
    }
    if (this.mediaMode === 'text') {
      return `${base}\nCreate a strong text caption; the image is optional context.`;
    }
    return base;
  }

  private loadPrompt(account: AccountWorkspaceAccount): string {
    const profileAccountId = this.selectedProfileAccountId() || account.id;
    if (this.selectedProfilePlatform === 'x') {
      return (
        this.accountForm.xPrompt ||
        account.prompt ||
        localStorage.getItem(this.promptKey(profileAccountId, 'x')) ||
        this.defaultPrompt(account, 'x')
      );
    }
    return (
      this.accountForm.threadsPrompt ||
        account.prompt ||
        localStorage.getItem(this.promptKey(profileAccountId, 'threads')) ||
        this.defaultPrompt(account, 'threads')
    );
  }

  private savePromptSilently(account: AccountWorkspaceAccount): void {
    this.applySelectedProfileContent();
    const profileAccountId = this.selectedProfileAccountId() || account.id;
    localStorage.setItem(this.promptKey(profileAccountId, this.selectedProfilePlatform), this.accountPrompt.trim());
  }

  private promptKey(accountId: string, platform: 'x' | 'threads'): string {
    return `bts.accountPrompt.${accountId}.${platform}`;
  }

  private defaultPrompt(account: AccountWorkspaceAccount, platform: 'x' | 'threads'): string {
    const profileName = platform === 'x' ? 'X' : 'Threads';
    return [
      `Write posts for ${this.displayPlatformName(account, platform)} on ${profileName}.`,
      'Keep the voice personal and specific.',
      'Avoid generic marketing language.',
      `Write like a real person who knows this ${profileName} audience.`,
      'Use Ukrainian unless the account prompt says otherwise.'
    ].join('\n');
  }

  private applySelectedProfileContent(): void {
    const prompt = this.accountPrompt.trim();
    if (this.selectedProfilePlatform === 'x') {
      this.accountForm.xPrompt = prompt;
      this.accountForm.xDefaultPostCount = this.postCount;
      return;
    }
    this.accountForm.threadsPrompt = prompt;
    this.accountForm.threadsDefaultPostCount = this.postCount;
  }

  private selectedProfileLanguage(): string {
    return this.selectedProfilePlatform === 'x'
      ? this.accountForm.xLanguage || this.accountForm.language || 'uk'
      : this.accountForm.threadsLanguage || this.accountForm.language || 'uk';
  }

  private selectedProfilePrompt(): string {
    return this.selectedProfilePlatform === 'x'
      ? (this.accountForm.xPrompt || this.accountForm.prompt || this.accountPrompt)
      : (this.accountForm.threadsPrompt || this.accountForm.prompt || this.accountPrompt);
  }

  private selectedProfilePromptFromResult(result: AccountConfig): string {
    return this.selectedProfilePlatform === 'x'
      ? (result.xPrompt || result.prompt)
      : (result.threadsPrompt || result.prompt);
  }

  private draftWorkspaceAccount(): AccountWorkspaceAccount {
    return {
      id: this.accountForm.id || '',
      label: this.accountForm.label || 'New account',
      prompt: this.accountPrompt || this.sharedPromptFallback(),
      language: this.accountForm.language || 'uk',
      defaultPostCount: this.accountForm.defaultPostCount || 4,
      xAccountLabel: this.accountForm.xAccountLabel || 'X account is not configured',
      xModeLabel: this.accountForm.xPublishMode || 'selenium',
      xConfigured: !!this.selectedProfileConfigured({
        id: this.accountForm.id || '',
        label: this.accountForm.label || '',
        prompt: this.accountForm.prompt,
        language: this.accountForm.language,
        defaultPostCount: this.accountForm.defaultPostCount,
        xAccountLabel: this.accountForm.xAccountLabel,
        xModeLabel: this.accountForm.xPublishMode,
        xConfigured: false,
        xReady: 0,
        xFailed: 0,
        threadsAccountLabel: this.accountForm.threadsAccountLabel,
        threadsConfigured: false,
        threadsReady: 0,
        threadsFailed: 0,
        mediaAttached: 0,
        textOnly: 0,
        published: 0
      }),
      threadsAccountLabel: this.accountForm.threadsAccountLabel || 'Threads account is not configured',
      threadsConfigured: !!this.accountForm.threadsUserId?.trim(),
      xReady: 0,
      xFailed: 0,
      threadsReady: 0,
      threadsFailed: 0,
      mediaAttached: 0,
      textOnly: 0,
      published: 0
    };
  }

  private loadAccountForm(account: AccountWorkspaceAccount): void {
    const config = this.accountConfigs.find((item) => item.id === account.id);
    this.accountForm = config ? this.toForm(config) : {
      ...this.blankAccountForm(),
      id: account.id,
      label: account.label,
      prompt: this.loadPrompt(account),
      language: account.language || 'uk',
      defaultPostCount: account.defaultPostCount || 4,
      xAccountLabel: account.xAccountLabel,
      threadsAccountLabel: account.threadsAccountLabel
    };
    this.accountPrompt = this.loadPrompt(account);
    this.postCount = this.selectedProfileCount();
  }

  private blankAccountForm(): AccountConfigRequest & { source?: string } {
    return {
      id: '',
      label: '',
      source: 'ui',
      prompt: [
        'Write posts for this account.',
        'Keep the voice personal and specific.',
        'Avoid generic marketing language.',
        'Use Ukrainian unless the account needs another language.'
      ].join('\n'),
      language: 'uk',
      defaultPostCount: 4,
      xPrompt: [
        'Write X posts for this account.',
        'Keep them short, natural, and specific.',
        'Avoid generic marketing language.'
      ].join('\n'),
      xLanguage: 'uk',
      xDefaultPostCount: 4,
      xAccountLabel: '',
      xAccessToken: '',
      xClientId: '',
      xClientSecret: '',
      xRedirectUri: 'http://127.0.0.1:3000/callback',
      xScopes: 'tweet.read tweet.write users.read',
      xApiKey: '',
      xApiSecret: '',
      xAccessTokenSecret: '',
      xRefreshToken: '',
      xPublishMode: 'selenium',
      xBrowser: 'chrome',
      xBrowserProfileDir: '',
      xBrowserHeadless: false,
      threadsPrompt: [
        'Write Threads posts for this account.',
        'Use a conversational, human voice.',
        'Make each post feel native to Threads.'
      ].join('\n'),
      threadsLanguage: 'uk',
      threadsDefaultPostCount: 4,
      threadsAccountLabel: '',
      threadsAccessToken: '',
      threadsUserId: '',
      threadsAppId: '',
      threadsAppSecret: '',
      threadsRedirectUri: 'http://127.0.0.1:3001/callback'
    };
  }

  private toForm(config: AccountConfig): AccountConfigRequest & { source?: string } {
    const base = { ...this.blankAccountForm(), ...config };
    return {
      ...base,
      xPrompt: base.xPrompt || base.prompt,
      xLanguage: base.xLanguage || base.language,
      xDefaultPostCount: base.xDefaultPostCount || base.defaultPostCount,
      threadsPrompt: base.threadsPrompt || base.prompt,
      threadsLanguage: base.threadsLanguage || base.language,
      threadsDefaultPostCount: base.threadsDefaultPostCount || base.defaultPostCount
    };
  }

  private selectedProfileCount(): number {
    return this.selectedProfilePlatform === 'x'
      ? this.accountForm.xDefaultPostCount || this.accountForm.defaultPostCount || 4
      : this.accountForm.threadsDefaultPostCount || this.accountForm.defaultPostCount || 4;
  }

  private sharedPromptFallback(): string {
    return this.accountForm.prompt || this.accountForm.xPrompt || this.accountForm.threadsPrompt || this.accountPrompt.trim();
  }

  private cleanAccountRequest(form: AccountConfigRequest, enforceUniqueId: boolean): AccountConfigRequest {
    const base = Object.fromEntries(
      Object.entries(form).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value])
    ) as AccountConfigRequest & { [key: string]: unknown };
    const rawId = (base.id ?? '').trim() || (base.label ?? '').trim();
    const normalizedBaseId = this.normalizeAccountId(rawId);
    const baseScopedId = this.scopedAccountId(normalizedBaseId);
    const profileScopedId = enforceUniqueId ? this.ensureUniqueProfileId(baseScopedId) : baseScopedId;

    if (this.selectedProfilePlatform === 'x') {
      return {
        ...base,
        id: profileScopedId,
        threadsPrompt: '',
        threadsLanguage: '',
        threadsDefaultPostCount: 0,
        threadsAccountLabel: '',
        threadsAccessToken: '',
        threadsUserId: '',
        threadsAppId: '',
        threadsAppSecret: '',
        threadsRedirectUri: ''
      };
    }

      return {
        ...base,
        id: profileScopedId,
        xPrompt: '',
        xLanguage: '',
        xDefaultPostCount: 0,
      xAccountLabel: '',
      xAccessToken: '',
      xClientId: '',
      xClientSecret: '',
      xRedirectUri: '',
      xScopes: '',
      xApiKey: '',
      xApiSecret: '',
      xAccessTokenSecret: '',
      xRefreshToken: '',
      xPublishMode: '',
      xBrowser: '',
      xBrowserProfileDir: '',
      xBrowserHeadless: false
    };
  }

  private scopedAccountId(accountId: string): string {
    const trimmed = (accountId ?? '').trim();
    if (!trimmed) {
      return '';
    }
    const lowered = trimmed.toLowerCase();
    if (lowered.endsWith('-x') || lowered.endsWith('-threads')) {
      return trimmed;
    }
    return `${trimmed}-${this.selectedProfilePlatform}`;
  }

  private normalizeAccountId(accountId: string): string {
    const safe = (accountId ?? '').trim().toLowerCase().replaceAll(/[^a-z0-9_-]+/g, '-');
    const compact = safe.replace(/-+/g, '-');
    return compact.replace(/^-+|-+$/g, '') || 'account';
  }

  private ensureUniqueProfileId(baseScopedId: string): string {
    const normalizedCurrent = (baseScopedId ?? '').trim();
    if (!normalizedCurrent) {
      return '';
    }

    const currentProfileAccountId = this.selectedProfileAccountId();
    if (!this.creatingAccount && normalizedCurrent === currentProfileAccountId) {
      return normalizedCurrent;
    }

    const usedIds = new Set(
      this.accountConfigs
        .map((config) => (config.id ?? '').trim().toLowerCase())
        .filter((value) => value.length > 0)
    );

    if (!usedIds.has(normalizedCurrent.toLowerCase())) {
      return normalizedCurrent;
    }

    let suffix = 2;
    while (suffix < 9999) {
      const candidate = `${normalizedCurrent}-${suffix}`;
      if (!usedIds.has(candidate.toLowerCase())) {
        return candidate;
      }
      suffix += 1;
    }
    return normalizedCurrent;
  }
}
