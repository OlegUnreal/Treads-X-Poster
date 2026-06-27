import { AsyncPipe, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { QueuePost, QueuePostUpsertRequest } from '../models/dashboard.models';
import { DashboardService } from '../services/dashboard.service';
import { AdminUiStateService } from '../services/admin-ui-state.service';

@Component({
  selector: 'app-queue-page',
  standalone: true,
  imports: [AsyncPipe, DatePipe, NgFor, NgIf, FormsModule],
  template: `
    <section class="page" *ngIf="ui.vm$ | async as vm">
      <header class="page-head">
        <div>
          <p class="eyebrow">Queue</p>
          <h1>Upcoming Posts</h1>
          <p>Only editable, upcoming posts are shown here. Posted history is hidden to keep the page calm.</p>
        </div>
      </header>

      <article class="panel">
        <p class="feedback" *ngIf="ui.actionResult$ | async as result" [class.error]="!result.success">
          <strong>{{ result.command }}</strong>
          <span>{{ result.message }}</span>
        </p>

        <div class="queue-list" *ngIf="ui.editableQueue(vm.queue).length > 0; else emptyState">
          <article class="queue-card" *ngFor="let post of ui.editableQueue(vm.queue)">
            <div class="meta">
              <div class="meta-copy">
                <small>{{ post.createdAt | date:'yyyy-MM-dd HH:mm' }}</small>
              </div>
              <span class="badge">{{ post.status }}</span>
            </div>

            <div class="summary">
              <div class="image-thumb" *ngIf="post.imageUrl">
                <img [src]="post.imageUrl" alt="Queue preview" />
              </div>
              <p class="text-preview">{{ post.text }}</p>
            </div>

            <div class="actions split">
              <button type="button" (click)="toggleEditor(post.id)">
                {{ expandedPostId === post.id ? 'Close Editor' : 'Edit Post' }}
              </button>
              <button type="button" class="ghost" *ngIf="expandedPostId === post.id" (click)="saveQueuePost(post)">Save Changes</button>
            </div>

            <div class="editor" *ngIf="expandedPostId === post.id">
              <div class="form-grid">
                <label>
                  <span>Topic</span>
                  <input [ngModel]="draftFor(post).topic" (ngModelChange)="draftFor(post).topic = $event" />
                </label>
                <label>
                  <span>Status</span>
                  <input [ngModel]="draftFor(post).status" (ngModelChange)="draftFor(post).status = $event" />
                </label>
                <label>
                  <span>Language</span>
                  <input [ngModel]="draftFor(post).language" (ngModelChange)="draftFor(post).language = $event" />
                </label>
                <label>
                  <span>Tone</span>
                  <input [ngModel]="draftFor(post).tone" (ngModelChange)="draftFor(post).tone = $event" />
                </label>
                <label class="wide">
                  <span>Platforms (comma separated)</span>
                  <input [ngModel]="draftFor(post).platformsText" (ngModelChange)="draftFor(post).platformsText = $event" />
                </label>
                <label class="wide">
                  <span>Real Photo Idea</span>
                  <input [ngModel]="draftFor(post).visualHint" (ngModelChange)="draftFor(post).visualHint = $event" />
                </label>
                <label class="wide">
                  <span>Image URL</span>
                  <input [ngModel]="draftFor(post).imageUrl" (ngModelChange)="draftFor(post).imageUrl = $event" />
                </label>
                <label class="wide">
                  <span>Source Page</span>
                  <input [ngModel]="draftFor(post).imageSourcePage" (ngModelChange)="draftFor(post).imageSourcePage = $event" />
                </label>
                <label class="wide">
                  <span>Text</span>
                  <textarea [ngModel]="draftFor(post).text" (ngModelChange)="draftFor(post).text = $event" rows="6"></textarea>
                </label>
              </div>
            </div>
          </article>
        </div>

        <ng-template #emptyState>
          <div class="empty">
            There are no editable upcoming posts right now.
          </div>
        </ng-template>
      </article>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .page { display: grid; gap: 24px; }
    .eyebrow { margin: 0 0 10px; text-transform: uppercase; letter-spacing: 0.14em; font: 700 12px/1.2 "Segoe UI", sans-serif; color: #8a5a24; }
    h1 { margin: 0; font-size: clamp(2.1rem, 4vw, 3.4rem); }
    .page-head p { color: #52606d; font: 500 15px/1.6 "Segoe UI", sans-serif; margin: 10px 0 0; }
    .panel {
      padding: 24px;
      background: rgba(255,255,255,0.78);
      border: 1px solid rgba(31,41,51,0.08);
      border-radius: 24px;
      box-shadow: 0 24px 50px rgba(69,58,42,0.12);
      margin: 0 auto;
      width: 100%;
    }
    .queue-list {
      display: grid;
      gap: 16px;
      max-width: 760px;
      margin: 0 auto;
    }
    .queue-card {
      padding: 18px 20px;
      border: 1px solid rgba(31,41,51,0.08);
      border-radius: 24px;
      background: rgba(255,255,255,0.82);
      max-width: 760px;
      box-shadow: 0 14px 30px rgba(69,58,42,0.08);
    }
    .queue-card:first-child { border-top: 0; }
    .meta { display: flex; justify-content: space-between; gap: 16px; align-items: center; margin-bottom: 12px; }
    .meta-copy { display: grid; gap: 4px; }
    .meta-copy small { color: #52606d; font: 600 12px/1.5 "Segoe UI", sans-serif; }
    .badge {
      display: inline-flex;
      align-items: center;
      padding: 8px 12px;
      border-radius: 999px;
      background: rgba(15,118,110,0.1);
      color: #0f766e;
      font: 700 12px/1 "Segoe UI", sans-serif;
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }
    .summary {
      display: grid;
      grid-template-columns: 88px minmax(0, 1fr);
      gap: 12px;
      align-items: start;
      max-width: 700px;
    }
    .image-thumb {
      width: 88px;
      height: 88px;
      border-radius: 18px;
      overflow: hidden;
      background: rgba(31,41,51,0.06);
      border: 1px solid rgba(31,41,51,0.08);
      flex-shrink: 0;
    }
    .image-thumb img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
    .text-preview {
      margin: 0;
      color: #243b53;
      font: 500 15px/1.65 "Segoe UI", sans-serif;
      min-width: 0;
    }
    .form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 12px; }
    .wide { grid-column: 1 / -1; }
    .form-grid label { display: grid; gap: 8px; }
    .form-grid span { font-size: 12px; text-transform: uppercase; letter-spacing: 0.08em; color: #52606d; font-family: "Segoe UI", sans-serif; }
    .form-grid input, .form-grid textarea {
      width: 100%;
      border: 1px solid rgba(31,41,51,0.12);
      border-radius: 14px;
      padding: 12px 14px;
      background: rgba(255,255,255,0.92);
      color: #243b53;
      font: 500 14px/1.5 "Segoe UI", sans-serif;
      resize: vertical;
    }
    .actions { display: grid; grid-template-columns: 1fr; gap: 12px; margin-top: 14px; }
    .actions.split { grid-template-columns: repeat(2, minmax(0, max-content)); align-items: center; }
    button {
      border: 0;
      border-radius: 999px;
      padding: 12px 16px;
      font: 700 13px/1.2 "Segoe UI", sans-serif;
      background: linear-gradient(135deg, #1f6feb, #0f766e);
      color: white;
      cursor: pointer;
      box-shadow: 0 16px 30px rgba(21, 48, 74, 0.2);
    }
    button.ghost {
      background: rgba(255,255,255,0.9);
      color: #243b53;
      border: 1px solid rgba(31,41,51,0.12);
      box-shadow: none;
    }
    .editor {
      margin-top: 18px;
      padding-top: 18px;
      border-top: 1px dashed rgba(31,41,51,0.12);
    }
    .feedback {
      margin: 0 0 18px;
      padding: 12px 14px;
      border-radius: 14px;
      background: rgba(15,118,110,0.09);
      color: #0f5132;
      font: 600 14px/1.5 "Segoe UI", sans-serif;
      display: grid; gap: 4px;
    }
    .feedback.error { background: rgba(154,52,18,0.09); color: #7c2d12; }
    .empty { color: #52606d; font: 600 15px/1.5 "Segoe UI", sans-serif; }
    @media (max-width: 900px) {
      .form-grid { grid-template-columns: 1fr; }
      .summary { grid-template-columns: 88px minmax(0, 1fr); }
      .image-thumb {
        width: 88px;
        height: 88px;
      }
    }
    @media (min-width: 901px) {
      .panel {
        max-width: 860px;
      }
    }
  `]
})
export class QueuePageComponent {
  protected readonly ui = inject(AdminUiStateService);
  private readonly dashboardService = inject(DashboardService);
  protected expandedPostId: string | null = null;
  private readonly queueDrafts = new Map<string, {
    topic: string;
    text: string;
    visualHint: string;
    imageUrl: string;
    imageSourcePage: string;
    status: string;
    language: string;
    tone: string;
    platformsText: string;
  }>();

  protected saveQueuePost(post: QueuePost): void {
    const draft = this.draftFor(post);
    const payload: QueuePostUpsertRequest = this.ui.sanitizeQueueUpsertRequest({
      topic: draft.topic,
      text: draft.text,
      visualHint: draft.visualHint,
      imageUrl: draft.imageUrl,
      imageSourcePage: draft.imageSourcePage,
      imageAttribution: post.imageAttribution,
      imageLicense: post.imageLicense,
      status: draft.status,
      language: draft.language,
      tone: draft.tone,
      platforms: this.ui.parsePlatforms(draft.platformsText)
    });

    this.dashboardService.updateQueuePost(post.id, payload).subscribe(() => {
      this.ui.pushActionResult({
        success: true,
        command: 'update-queue-post',
        message: `Post ${post.id} updated.`
      });
      this.queueDrafts.delete(post.id);
      this.expandedPostId = null;
    });
  }

  protected toggleEditor(postId: string): void {
    this.expandedPostId = this.expandedPostId === postId ? null : postId;
  }

  protected draftFor(post: QueuePost) {
    const existing = this.queueDrafts.get(post.id);
    if (existing) {
      return existing;
    }

    const created = {
      topic: post.topic ?? '',
      text: post.text ?? '',
      visualHint: post.visualHint ?? '',
      imageUrl: post.imageUrl ?? '',
      imageSourcePage: post.imageSourcePage ?? '',
      status: post.status ?? 'ready',
      language: post.language ?? 'uk',
      tone: post.tone ?? '',
      platformsText: (post.platforms ?? []).join(', ')
    };
    this.queueDrafts.set(post.id, created);
    return created;
  }
}
