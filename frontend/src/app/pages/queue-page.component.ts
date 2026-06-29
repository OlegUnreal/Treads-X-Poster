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
      <header class="page-head d-flex flex-wrap justify-content-between align-items-end gap-2">
        <div>
          <p class="eyebrow">Queue</p>
          <h1>Upcoming</h1>
        </div>
        <div class="toolbar">
          <select
            class="form-select form-select-sm"
            [ngModel]="selectedQueueProfileId(vm.summary.publisherAccounts.activeAccountId)"
            (ngModelChange)="switchQueueProfile($event)"
          >
            <option
              *ngFor="let profile of ui.publishingProfiles(vm.summary.publisherAccounts)"
              [ngValue]="profile.id"
            >
              {{ profile.name }} · {{ profile.subtitle }}
            </option>
          </select>
          <button type="button" class="btn btn-outline-primary btn-sm" (click)="fillMissingPhotos()">Fill Photos</button>
          <button type="button" class="btn btn-outline-secondary btn-sm" (click)="cleanDuplicatePhotos()">Clean Photos</button>
        </div>
      </header>

      <article class="panel">
        <p class="feedback" *ngIf="ui.actionResult$ | async as result" [class.error]="!result.success">
          <strong>{{ result.command }}</strong>
          <span>{{ result.message }}</span>
        </p>

        <div class="queue-list" *ngIf="ui.editableQueue(vm.queue).length > 0; else emptyState">
          <article class="queue-card" *ngFor="let post of ui.editableQueue(vm.queue); let index = index; let last = last">
            <div class="meta">
              <span>{{ post.createdAt | date:'yyyy-MM-dd HH:mm' }}</span>
              <span class="badge text-bg-light">{{ post.status }}</span>
            </div>

            <div class="summary" [class.no-image]="!post.imageUrl">
              <div class="image-thumb" *ngIf="post.imageUrl">
                <img [src]="post.imageUrl" alt="Queue preview" />
              </div>
              <p>{{ post.text }}</p>
            </div>

            <div class="actions">
              <button type="button" class="btn btn-outline-secondary btn-sm" [disabled]="index === 0" (click)="movePost(post, 'up')">Up</button>
              <button type="button" class="btn btn-outline-secondary btn-sm" [disabled]="last" (click)="movePost(post, 'down')">Down</button>
              <button type="button" class="btn btn-primary btn-sm" (click)="toggleEditor(post.id)">
                {{ expandedPostId === post.id ? 'Close' : 'Edit' }}
              </button>
              <button type="button" class="btn btn-outline-primary btn-sm" *ngIf="expandedPostId === post.id" (click)="saveQueuePost(post)">Save</button>
              <button type="button" class="btn btn-outline-secondary btn-sm" *ngIf="post.imageUrl" (click)="removePhoto(post)">Remove Photo</button>
              <button type="button" class="btn btn-outline-danger btn-sm" (click)="deletePost(post)">Delete</button>
            </div>

            <div class="editor" *ngIf="expandedPostId === post.id">
              <div class="row g-2">
                <div class="col-md-6">
                  <label class="field">
                    <span>Topic</span>
                    <input class="form-control form-control-sm" [ngModel]="draftFor(post).topic" (ngModelChange)="draftFor(post).topic = $event" />
                  </label>
                </div>
                <div class="col-md-3">
                  <label class="field">
                    <span>Status</span>
                    <input class="form-control form-control-sm" [ngModel]="draftFor(post).status" (ngModelChange)="draftFor(post).status = $event" />
                  </label>
                </div>
                <div class="col-md-3">
                  <label class="field">
                    <span>Language</span>
                    <input class="form-control form-control-sm" [ngModel]="draftFor(post).language" (ngModelChange)="draftFor(post).language = $event" />
                  </label>
                </div>
                <div class="col-12">
                  <label class="field">
                    <span>Text</span>
                    <textarea class="form-control" [ngModel]="draftFor(post).text" (ngModelChange)="draftFor(post).text = $event" rows="4"></textarea>
                  </label>
                </div>
                <div class="col-md-6">
                  <label class="field">
                    <span>Image URL</span>
                    <input class="form-control form-control-sm" [ngModel]="draftFor(post).imageUrl" (ngModelChange)="draftFor(post).imageUrl = $event" />
                  </label>
                </div>
                <div class="col-md-6">
                  <label class="field">
                    <span>Source Page</span>
                    <input class="form-control form-control-sm" [ngModel]="draftFor(post).imageSourcePage" (ngModelChange)="draftFor(post).imageSourcePage = $event" />
                  </label>
                </div>
              </div>
            </div>
          </article>
        </div>

        <ng-template #emptyState>
          <div class="empty">No editable posts in this profile queue.</div>
        </ng-template>
      </article>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .page { display: grid; gap: 12px; }
    .page-head h1 { margin: 0; font-size: 28px; line-height: 1.05; }
    .eyebrow, .field span {
      margin: 0 0 4px;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: #64748b;
      font: 700 11px/1.2 "Segoe UI", sans-serif;
    }
    .toolbar { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
    .toolbar select { min-width: 280px; }
    .panel {
      padding: 12px;
      background: #fff;
      border: 1px solid #dde3ea;
      border-radius: 12px;
      box-shadow: 0 8px 22px rgba(15, 23, 42, 0.05);
    }
    .queue-list { display: grid; gap: 10px; }
    .queue-card {
      padding: 12px;
      border: 1px solid #dde3ea;
      border-radius: 10px;
      background: #f8fafc;
    }
    .meta { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 8px; color: #64748b; font: 700 12px/1.3 "Segoe UI", sans-serif; }
    .summary { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 10px; align-items: start; }
    .summary.no-image { grid-template-columns: 1fr; }
    .summary p { margin: 0; color: #17212b; font: 500 14px/1.5 "Segoe UI", sans-serif; }
    .image-thumb { width: 72px; height: 72px; border-radius: 8px; overflow: hidden; background: #e2e8f0; }
    .image-thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .actions { display: flex; gap: 6px; flex-wrap: wrap; justify-content: flex-end; margin-top: 10px; }
    .editor { margin-top: 10px; padding-top: 10px; border-top: 1px dashed #cbd5e1; }
    .field { display: grid; gap: 4px; }
    .feedback {
      margin: 0 0 10px;
      padding: 8px 10px;
      border-radius: 8px;
      background: #e9f7ef;
      color: #146c43;
      font: 600 13px/1.35 "Segoe UI", sans-serif;
      display: grid;
      gap: 2px;
    }
    .feedback.error { background: #fff1f2; color: #be123c; }
    .empty { color: #64748b; font: 700 14px/1.4 "Segoe UI", sans-serif; padding: 10px; }
    @media (max-width: 760px) { .toolbar { justify-content: stretch; } .toolbar select, .toolbar button { width: 100%; } }
  `]
})
export class QueuePageComponent {
  protected readonly ui = inject(AdminUiStateService);
  private readonly dashboardService = inject(DashboardService);
  protected expandedPostId: string | null = null;
  protected queuePlatform: 'x' | 'threads' = 'x';
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

  protected selectedQueueProfileId(activeAccountId: string): string {
    return `${activeAccountId}:${this.queuePlatform}`;
  }

  protected switchQueueProfile(profileId: string): void {
    const [accountId, platform] = profileId.split(':');
    this.queuePlatform = platform === 'threads' ? 'threads' : 'x';
    this.ui.setQueuePlatform(this.queuePlatform);
    this.dashboardService.switchActiveAccount(accountId).subscribe(() => {
      this.ui.pushActionResult({
        success: true,
        command: 'switch-account',
        message: 'Queue profile changed.'
      });
      this.queueDrafts.clear();
      this.expandedPostId = null;
    });
  }

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
      platforms: [this.queuePlatform]
    });

    this.dashboardService.updateQueuePost(post.id, payload, this.queuePlatform).subscribe(() => {
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

  protected movePost(post: QueuePost, direction: 'up' | 'down'): void {
    this.dashboardService.moveQueuePost(post.id, direction, this.queuePlatform).subscribe((result) => {
      this.ui.pushActionResult(result);
      this.queueDrafts.clear();
    });
  }

  protected deletePost(post: QueuePost): void {
    const preview = (post.topic || post.text || post.id).slice(0, 80);
    if (!window.confirm(`Delete this queued post?\n\n${preview}`)) {
      return;
    }
    this.dashboardService.deleteQueuePost(post.id, this.queuePlatform).subscribe((result) => {
      this.ui.pushActionResult(result);
      this.queueDrafts.delete(post.id);
      if (this.expandedPostId === post.id) {
        this.expandedPostId = null;
      }
    });
  }

  protected cleanDuplicatePhotos(): void {
    this.dashboardService.cleanDuplicateQueueImages(this.queuePlatform).subscribe((result) => {
      this.ui.pushActionResult(result);
      this.queueDrafts.clear();
    });
  }

  protected fillMissingPhotos(): void {
    this.dashboardService.fillMissingQueuePhotos(this.queuePlatform).subscribe((result) => {
      this.ui.pushActionResult(result);
      this.queueDrafts.clear();
    });
  }

  protected removePhoto(post: QueuePost): void {
    const payload: QueuePostUpsertRequest = this.ui.sanitizeQueueUpsertRequest({
      topic: post.topic,
      text: post.text,
      visualHint: post.visualHint,
      imageUrl: '',
      imageSourcePage: '',
      imageAttribution: '',
      imageLicense: '',
      status: post.status,
      language: post.language ?? 'uk',
      tone: post.tone ?? '',
      platforms: [this.queuePlatform]
    });

    this.dashboardService.updateQueuePost(post.id, payload, this.queuePlatform).subscribe(() => {
      this.ui.pushActionResult({
        success: true,
        command: 'update-queue-post',
        message: 'Photo removed from queued post.'
      });
      this.queueDrafts.delete(post.id);
    });
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
