import { AsyncPipe, NgFor, NgIf } from '@angular/common';
import { Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ActionResult, BrowserXPublishRequest, QueuePost } from '../models/dashboard.models';
import { DashboardService } from '../services/dashboard.service';
import { AdminUiStateService } from '../services/admin-ui-state.service';

@Component({
  selector: 'app-publish-page',
  standalone: true,
  imports: [AsyncPipe, NgFor, NgIf, FormsModule],
  template: `
    <section class="page" *ngIf="ui.vm$ | async as vm">
      <header class="page-head">
        <div>
          <p class="eyebrow">Send Posts</p>
          <h1>Publish</h1>
          <p>Only the actions related to posting live here.</p>
        </div>
      </header>

      <section class="grid">
        <article class="panel">
          <div class="panel-head">
            <h2>Quick Publish</h2>
            <p>The shortest path when you just want one post to go out now.</p>
          </div>
          <div class="account-switcher">
            <label>
              <span>Active Account</span>
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
            <dl>
              <div>
                <dt>X</dt>
                <dd>{{ vm.summary.publisherAccounts.xAccountLabel }}</dd>
              </div>
              <div>
                <dt>Threads</dt>
                <dd>{{ vm.summary.publisherAccounts.threadsAccountLabel }}</dd>
              </div>
            </dl>
            <div class="setup-note" *ngIf="needsThreadsSetup(vm.summary.publisherAccounts.threadsAccountLabel)">
              <strong>Threads is not connected</strong>
              <span>Keep generating posts if needed, but connect Threads before publishing there.</span>
            </div>
          </div>
          <div class="focus-actions">
            <button type="button" [disabled]="busyAction !== null" (click)="publishThreadNow()">
              {{ busyAction === 'publish-thread' ? 'Publishing...' : 'Publish 1 Thread' }}
            </button>
            <button type="button" [disabled]="busyAction !== null" (click)="publishXNow()">
              {{ busyAction === 'publish-x' ? 'Publishing...' : 'Publish 1 X Post' }}
            </button>
          </div>
          <div class="actions split">
            <button type="button" class="ghost" (click)="showSupportActions = !showSupportActions">
              {{ showSupportActions ? 'Hide Support Actions' : 'Show Support Actions' }}
            </button>
          </div>
          <div class="support-actions" *ngIf="showSupportActions">
            <div class="actions">
              <button type="button" class="secondary" [disabled]="busyAction !== null" (click)="generateMorePosts()">
                {{ busyAction === 'auto-create' ? 'Generating...' : 'Generate More Posts' }}
              </button>
            </div>
          </div>
          <p class="feedback" *ngIf="ui.actionResult$ | async as result" [class.error]="!result.success">
            <strong>{{ result.command }}</strong>
            <span>{{ result.message }}</span>
          </p>
        </article>

        <article class="panel">
          <div class="panel-head">
            <h2>X Composer</h2>
            <p>One clean flow: choose a queued post, review it, then send it.</p>
          </div>
          <ol class="steps">
            <li>Choose a queued X post or keep manual text.</li>
            <li>Review the text below.</li>
            <li>Use Send With Selenium.</li>
          </ol>
          <div class="form-grid compact">
            <label class="wide">
              <span>Step 1: Choose Queue Post</span>
              <select [ngModel]="selectedXPostId" (ngModelChange)="selectXPostById($event, vm.queue)">
                <option [ngValue]="null">Manual text only</option>
                <option *ngFor="let post of ui.xReadyPosts(vm.queue)" [ngValue]="post.id">
                  {{ post.topic || 'Untitled' }} - {{ post.id }}
                </option>
              </select>
            </label>
            <label class="wide">
              <span>Step 2: Review Text</span>
              <textarea [(ngModel)]="xComposerText" rows="8"></textarea>
            </label>
          </div>
          <div class="focus-actions single-column">
            <button type="button" [disabled]="busyAction !== null" (click)="sendWithSelenium()">
              {{ busyAction === 'publish-x-via-selenium' ? 'Sending...' : 'Step 3: Send With Selenium' }}
            </button>
          </div>
          <div class="actions split">
            <button type="button" class="ghost" (click)="showFallbackActions = !showFallbackActions">
              {{ showFallbackActions ? 'Hide Fallback Options' : 'Show Fallback Options' }}
            </button>
          </div>
          <div class="support-actions" *ngIf="showFallbackActions">
            <div class="actions">
              <button type="button" class="secondary" (click)="openXComposer()">Open X Window</button>
              <button type="button" class="secondary" [disabled]="busyAction !== null" (click)="openXLoginBrowser()">
                {{ busyAction === 'open-x-login-browser' ? 'Opening...' : 'Open Selenium Login' }}
              </button>
            </div>
          </div>
        </article>
      </section>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .page { display: grid; gap: 24px; }
    .page-head p, .panel-head p { color: #52606d; font: 500 16px/1.6 "Segoe UI", sans-serif; margin: 10px 0 0; }
    .eyebrow { margin: 0 0 10px; text-transform: uppercase; letter-spacing: 0.14em; font: 700 12px/1.2 "Segoe UI", sans-serif; color: #8a5a24; }
    h1 { margin: 0; font-size: clamp(2.1rem, 4vw, 3.4rem); }
    .grid { display: grid; grid-template-columns: 0.9fr 1.1fr; gap: 18px; }
    .panel {
      padding: 24px;
      background: rgba(255,255,255,0.78);
      border: 1px solid rgba(31,41,51,0.08);
      border-radius: 24px;
      box-shadow: 0 24px 50px rgba(69,58,42,0.12);
    }
    .panel-head h2 { margin: 0; font-size: 28px; }
    .account-switcher {
      margin-top: 18px;
      padding: 14px;
      border: 1px solid rgba(31,41,51,0.10);
      border-radius: 16px;
      background: rgba(255,255,255,0.64);
      display: grid;
      gap: 12px;
      font-family: "Segoe UI", sans-serif;
    }
    .account-switcher label { display: grid; gap: 8px; }
    .account-switcher span, .account-switcher dt {
      color: #52606d;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }
    .account-switcher select {
      width: 100%;
      border: 1px solid rgba(31,41,51,0.12);
      border-radius: 12px;
      padding: 11px 12px;
      background: white;
      color: #243b53;
      font: 700 14px/1.4 "Segoe UI", sans-serif;
    }
    .account-switcher dl { margin: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .account-switcher dd {
      margin: 4px 0 0;
      color: #243b53;
      font: 700 14px/1.4 "Segoe UI", sans-serif;
      overflow-wrap: anywhere;
    }
    .setup-note {
      padding: 12px 14px;
      border-radius: 14px;
      background: rgba(154,52,18,0.09);
      color: #7c2d12;
      font: 600 14px/1.5 "Segoe UI", sans-serif;
      display: grid;
      gap: 4px;
    }
    .focus-actions {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
      margin-top: 18px;
    }
    .focus-actions.single-column { grid-template-columns: 1fr; }
    .actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 18px; }
    .actions.split { grid-template-columns: 1fr; }
    .support-actions {
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px dashed rgba(31,41,51,0.12);
    }
    .steps {
      margin: 18px 0 0;
      padding-left: 18px;
      color: #52606d;
      font: 500 14px/1.7 "Segoe UI", sans-serif;
      display: grid;
      gap: 4px;
    }
    .form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 18px; }
    .form-grid.compact { margin-top: 16px; }
    .wide { grid-column: 1 / -1; }
    .form-grid label { display: grid; gap: 8px; }
    .form-grid span { font-size: 12px; text-transform: uppercase; letter-spacing: 0.08em; color: #52606d; font-family: "Segoe UI", sans-serif; }
    .form-grid input, .form-grid select, .form-grid textarea {
      width: 100%;
      border: 1px solid rgba(31,41,51,0.12);
      border-radius: 14px;
      padding: 12px 14px;
      background: rgba(255,255,255,0.92);
      color: #243b53;
      font: 500 14px/1.5 "Segoe UI", sans-serif;
      resize: vertical;
    }
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
    button:disabled {
      cursor: wait;
      opacity: 0.62;
      box-shadow: none;
    }
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
    @media (max-width: 900px) { .grid, .actions, .form-grid { grid-template-columns: 1fr; } }
  `]
})
export class PublishPageComponent {
  protected readonly ui = inject(AdminUiStateService);
  private readonly dashboardService = inject(DashboardService);

  protected showSupportActions = false;
  protected showFallbackActions = false;
  protected selectedXPostId: string | null = null;
  protected xComposerText = '';
  protected busyAction: string | null = null;

  protected generateMorePosts(): void {
    this.runAction('auto-create', this.dashboardService.runAutoCreate());
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
    this.runAction('publish-thread', this.dashboardService.publishThread());
  }

  protected publishXNow(): void {
    this.runAction('publish-x', this.dashboardService.publishX());
  }

  protected needsThreadsSetup(label: string | null | undefined): boolean {
    return (label ?? '').toLowerCase().includes('not configured');
  }

  protected openXComposer(): void {
    if (!this.xComposerText.trim()) {
      this.ui.pushActionResult({
        success: false,
        command: 'open-x-composer',
        message: 'Enter or select text for the X composer first.'
      });
      return;
    }

    const url = `https://x.com/intent/tweet?text=${encodeURIComponent(this.xComposerText.trim())}`;
    window.open(url, '_blank', 'noopener,noreferrer,width=760,height=840');
    this.ui.pushActionResult({
      success: true,
      command: 'open-x-composer',
      message: 'Opened X composer window with prefilled text.'
    });
  }

  protected openXLoginBrowser(): void {
    this.runAction('open-x-login-browser', this.dashboardService.openXLoginBrowser());
  }

  protected sendWithSelenium(): void {
    if (!this.xComposerText.trim()) {
      this.ui.pushActionResult({
        success: false,
        command: 'publish-x-via-selenium',
        message: 'Enter or select text for X first.'
      });
      return;
    }

    const payload: BrowserXPublishRequest = {
      queuePostId: this.selectedXPostId,
      text: this.ui.stripBom(this.xComposerText).trim(),
      markPublished: true
    };

    this.runAction('publish-x-via-selenium', this.dashboardService.publishXViaBrowser(payload), (result) => {
      if (result.success) {
        this.selectedXPostId = null;
        this.xComposerText = '';
      }
    });
  }

  protected selectXPostById(id: string | null, queue: QueuePost[]): void {
    this.selectedXPostId = id;
    if (!id) {
      return;
    }

    const selected = this.ui.xReadyPosts(queue).find((post) => post.id === id);
    if (selected) {
      this.xComposerText = selected.text ?? '';
    }
  }

  private runAction(command: string, request$: Observable<ActionResult>, onResult?: (result: ActionResult) => void): void {
    if (this.busyAction !== null) {
      return;
    }

    this.busyAction = command;
    request$
      .pipe(finalize(() => this.busyAction = null))
      .subscribe({
        next: (result) => {
          onResult?.(result);
          this.ui.pushActionResult(result);
        },
        error: (error: HttpErrorResponse) => {
          this.ui.pushActionResult({
            success: false,
            command,
            message: this.describeHttpError(error)
          });
        }
      });
  }

  private describeHttpError(error: HttpErrorResponse): string {
    const message = typeof error.error === 'string'
      ? error.error
      : error.error?.message || error.message;
    return `Request failed (${error.status || 'network'}): ${message || 'Please try again.'}`;
  }
}
