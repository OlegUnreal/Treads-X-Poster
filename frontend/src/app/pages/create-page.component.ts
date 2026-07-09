import { AsyncPipe, NgFor, NgIf } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GeneratePromptResponse } from '../models/dashboard.models';
import { DashboardService } from '../services/dashboard.service';
import { AdminUiStateService } from '../services/admin-ui-state.service';
import { ContentConfigService, PromptPreset } from '../services/content-config.service';

@Component({
  selector: 'app-create-page',
  standalone: true,
  imports: [AsyncPipe, NgFor, NgIf, FormsModule],
  template: `
    <section class="page" *ngIf="ui.vm$ | async as vm">
      <header class="page-head">
        <div>
          <p class="eyebrow">Content</p>
          <h1>Create posts</h1>
        </div>
        <span class="mode-pill">{{ selectedPhotoFiles.length > 0 ? 'Photo mode' : 'Text mode' }}</span>
      </header>

      <article class="panel profile-panel">
        <div class="section-head">
          <h2>Accounts and platforms</h2>
          <span class="badge text-bg-light">{{ selectedTargetProfileIds.length }} selected</span>
        </div>
        <div class="profile-grid">
          <label
            class="profile-card"
            *ngFor="let profile of ui.publishingProfiles(vm.summary.publisherAccounts)"
            [class.active]="isTargetProfileSelected(profile.id)"
          >
            <input
              type="checkbox"
              [checked]="isTargetProfileSelected(profile.id)"
              (change)="toggleTargetProfile(profile.id, $event)"
            />
            <span class="avatar" [class.threads]="profile.platform === 'threads'">
              <img *ngIf="profile.avatarUrl" [src]="profile.avatarUrl" [alt]="profile.name" />
              <span *ngIf="!profile.avatarUrl">{{ ui.profileInitial(profile) }}</span>
            </span>
            <span class="profile-copy">
              <strong>{{ profile.name }}</strong>
              <small>{{ profile.subtitle }}</small>
            </span>
          </label>
        </div>
        <p class="validation" *ngIf="targetProfileError">{{ targetProfileError }}</p>
      </article>

      <article class="panel ai-panel">
        <div class="section-head">
          <h2>AI content</h2>
          <span class="hint">{{ selectedPhotoFiles.length > 0 ? 'One caption per photo' : 'Generate text posts' }}</span>
        </div>

        <div class="simple-grid">
          <label class="field">
            <span>Preset</span>
            <select class="form-select form-select-sm" [(ngModel)]="selectedPromptTemplateId" (ngModelChange)="applyPromptTemplate($event)">
              <option *ngFor="let template of promptTemplates" [ngValue]="template.id">
                {{ template.title }}
              </option>
            </select>
          </label>

          <label class="file-field">
            <span>Photos optional</span>
            <input class="form-control form-control-sm" type="file" accept="image/*" multiple (change)="selectPhotoBatch($event)" />
            <small>{{ selectedPhotoFiles.length ? selectedPhotoFiles.length + ' selected' : 'Leave empty for text posts' }}</small>
          </label>
        </div>

        <label class="field prompt-field">
          <span>Prompt</span>
          <textarea class="form-control" [(ngModel)]="generatorForm.prompt" rows="5"></textarea>
        </label>

        <div class="settings-row">
          <label class="field count-field" *ngIf="selectedPhotoFiles.length === 0">
            <span>Count</span>
            <input class="form-control form-control-sm" [(ngModel)]="generatorForm.count" type="number" min="1" max="12" />
          </label>
          <label class="form-check compact-check" *ngIf="selectedPhotoFiles.length === 0">
            <input class="form-check-input" [(ngModel)]="generatorForm.saveToQueue" type="checkbox" />
            <span class="form-check-label">Save to queue</span>
          </label>
          <label class="form-check compact-check" *ngIf="selectedPhotoFiles.length > 0">
            <input class="form-check-input" [(ngModel)]="photoBatchForm.publishNow" type="checkbox" />
            <span class="form-check-label">Publish now</span>
          </label>
          <button class="btn btn-primary btn-sm action-button" type="button" [disabled]="photoBatchBusy" (click)="createWithAi()">
            {{ photoBatchBusy ? 'Working...' : selectedPhotoFiles.length > 0 ? 'Create from photos' : 'Generate posts' }}
          </button>
        </div>

        <details class="advanced" open>
          <summary>Settings</summary>
          <div class="advanced-grid">
            <label class="field">
              <span>Language</span>
              <input class="form-control form-control-sm" [(ngModel)]="generatorForm.language" />
            </label>
          </div>
        </details>

        <p class="feedback" *ngIf="generationResultMessage">{{ generationResultMessage }}</p>
        <p class="feedback" *ngIf="photoBatchMessage" [class.error]="photoBatchError">{{ photoBatchMessage }}</p>
        <ol class="generated-results" *ngIf="generatedPosts.length > 0">
          <li *ngFor="let post of generatedPosts">{{ post }}</li>
        </ol>
      </article>

      <details class="panel manual-panel">
        <summary>
          <span>
            <strong>Manual post</strong>
            <small>Add ready text without AI</small>
          </span>
        </summary>

        <div class="manual-body">
          <label class="field">
            <span>Text</span>
            <textarea class="form-control" [(ngModel)]="newPostForm.text" rows="4"></textarea>
          </label>
          <div class="settings-row manual-row">
            <button type="button" class="btn btn-primary btn-sm action-button" (click)="createManualPost()">Add to queue</button>
          </div>
        </div>

        <p class="feedback" *ngIf="ui.actionResult$ | async as result" [class.error]="!result.success">
          <strong>{{ result.command }}</strong>
          <span>{{ result.message }}</span>
        </p>
      </details>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .page { display: grid; gap: 12px; }
    .page-head { display: flex; justify-content: space-between; align-items: end; gap: 12px; }
    .page-head h1 { margin: 0; font-size: 28px; line-height: 1.05; }
    .eyebrow, .field span, .file-field span {
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
    .section-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 10px; }
    .section-head h2 { margin: 0; font-size: 17px; font-weight: 800; }
    .hint { color: #64748b; font: 700 12px/1.2 "Segoe UI", sans-serif; }
    .profile-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 8px; }
    .profile-card {
      display: grid;
      grid-template-columns: auto 34px minmax(0, 1fr);
      align-items: center;
      gap: 9px;
      margin: 0;
      padding: 9px 10px;
      border: 1px solid #dde3ea;
      border-radius: 10px;
      background: #f8fafc;
      cursor: pointer;
    }
    .profile-card.active { border-color: #0d6efd; background: #edf5ff; }
    .profile-card input { width: 16px; height: 16px; }
    .avatar {
      width: 34px;
      height: 34px;
      display: grid;
      place-items: center;
      overflow: hidden;
      border-radius: 50%;
      color: #fff;
      font: 800 13px/1 "Segoe UI", sans-serif;
      background: #111827;
    }
    .avatar.threads { background: #5b21b6; }
    .avatar img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .profile-copy { min-width: 0; display: grid; gap: 1px; }
    .profile-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font: 800 13px/1.25 "Segoe UI", sans-serif; }
    .profile-copy small { color: #64748b; font: 600 12px/1.25 "Segoe UI", sans-serif; }
    .simple-grid { display: grid; grid-template-columns: minmax(180px, 280px) minmax(220px, 1fr); gap: 10px; }
    .field, .file-field { display: grid; gap: 4px; }
    .file-field small { color: #64748b; font: 600 12px/1.2 "Segoe UI", sans-serif; }
    .prompt-field { margin-top: 10px; }
    .form-control, .form-select { border-radius: 8px; }
    textarea.form-control { resize: vertical; line-height: 1.45; }
    .settings-row {
      display: grid;
      grid-template-columns: 92px auto auto;
      gap: 10px;
      align-items: end;
      margin-top: 10px;
    }
    .compact-check {
      min-height: 31px;
      display: flex;
      align-items: center;
      gap: 7px;
      margin: 0;
      white-space: nowrap;
      color: #334155;
      font: 700 13px/1.2 "Segoe UI", sans-serif;
    }
    .action-button { min-width: 150px; }
    .advanced { margin-top: 10px; }
    .advanced summary {
      width: max-content;
      color: #475569;
      cursor: pointer;
      font: 800 13px/1.2 "Segoe UI", sans-serif;
    }
    .advanced-grid { display: grid; grid-template-columns: 140px minmax(220px, 1fr); gap: 10px; margin-top: 8px; }
    .manual-panel { padding: 0; overflow: hidden; }
    .manual-panel > summary {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 14px;
      cursor: pointer;
      list-style: none;
    }
    .manual-panel > summary::-webkit-details-marker { display: none; }
    .manual-panel strong { display: block; color: #17212b; font: 800 15px/1.25 "Segoe UI", sans-serif; }
    .manual-panel small { color: #64748b; font: 600 12px/1.3 "Segoe UI", sans-serif; }
    .manual-body { padding: 0 14px 14px; }
    .manual-row { display: flex; justify-content: flex-end; }
    .feedback {
      margin: 10px 0 0;
      padding: 8px 10px;
      border-radius: 8px;
      background: #e9f7ef;
      color: #146c43;
      font: 600 13px/1.35 "Segoe UI", sans-serif;
      display: grid;
      gap: 2px;
    }
    .feedback.error, .validation { background: #fff1f2; color: #be123c; }
    .validation { margin: 10px 0 0; padding: 8px 10px; border-radius: 8px; font: 700 13px/1.35 "Segoe UI", sans-serif; }
    .generated-results { margin: 10px 0 0; padding-left: 18px; color: #334155; font: 500 13px/1.5 "Segoe UI", sans-serif; }
    @media (max-width: 900px) {
      .page-head, .simple-grid, .settings-row, .advanced-grid, .manual-row { grid-template-columns: 1fr; display: grid; align-items: stretch; }
      .action-button { width: 100%; }
    }
  `]
})
export class CreatePageComponent {
  protected readonly ui = inject(AdminUiStateService);
  private readonly dashboardService = inject(DashboardService);
  private readonly contentConfig = inject(ContentConfigService);

  protected photoBatchBusy = false;
  protected photoBatchMessage = '';
  protected photoBatchError = false;
  protected generatedPosts: string[] = [];
  protected generationResultMessage = '';
  protected selectedPhotoFiles: File[] = [];
  protected selectedTargetProfileIds: string[] = [];
  protected targetProfileError = '';
  protected selectedPromptTemplateId = 'quiet-vlog';
  protected promptTemplates: PromptPreset[] = [];
  protected generatorForm = {
    prompt: '',
    topic: '',
    language: 'uk',
    count: 3,
    saveToQueue: true
  };
  protected photoBatchForm = {
    publishNow: false
  };
  protected newPostForm = {
    topic: '',
    text: '',
    visualHint: '',
    imageUrl: '',
    imageSourcePage: '',
    status: 'ready',
    language: 'uk'
  };

  constructor() {
    this.promptTemplates = this.contentConfig.loadPresets();
    const firstTemplate = this.promptTemplates[0];
    if (firstTemplate) {
      this.selectedPromptTemplateId = firstTemplate.id;
      this.applyPromptTemplate(firstTemplate.id);
    }
  }

  protected applyPromptTemplate(templateId: string): void {
    const template = this.promptTemplates.find((item) => item.id === templateId);
    if (!template) {
      return;
    }
    this.generatorForm = {
      ...this.generatorForm,
      prompt: template.prompt,
      topic: template.topic
    };
  }

  protected isTargetProfileSelected(profileId: string): boolean {
    return this.selectedTargetProfileIds.includes(profileId);
  }

  protected toggleTargetProfile(profileId: string, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    if (checked && !this.selectedTargetProfileIds.includes(profileId)) {
      this.selectedTargetProfileIds = [...this.selectedTargetProfileIds, profileId];
      this.targetProfileError = '';
      return;
    }
    if (!checked) {
      this.selectedTargetProfileIds = this.selectedTargetProfileIds.filter((id) => id !== profileId);
    }
  }

  protected selectPhotoBatch(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedPhotoFiles = Array.from(input.files ?? []);
    this.photoBatchMessage = '';
    this.photoBatchError = false;
  }

  protected createWithAi(): void {
    if (this.selectedPhotoFiles.length > 0) {
      this.createPhotoBatch();
      return;
    }
    this.generateFromPrompt();
  }

  private generateFromPrompt(): void {
    if (!this.ensureTargetProfiles()) {
      return;
    }
    this.photoBatchMessage = '';
    this.dashboardService.generatePrompt({
      ...this.generatorForm,
      topic: this.resolveAiTopic(),
      platforms: [],
      accountIds: [],
      targetProfiles: this.selectedTargetProfileIds
    }).subscribe((result) => this.handleGenerationResult(result));
  }

  private createPhotoBatch(): void {
    if (!this.ensureTargetProfiles()) {
      return;
    }
    this.photoBatchBusy = true;
    this.photoBatchError = false;
    this.photoBatchMessage = 'Generating captions...';
    this.generationResultMessage = '';
    this.generatedPosts = [];
    this.dashboardService.createPhotoBatch({
      photos: this.selectedPhotoFiles,
      prompt: this.generatorForm.prompt,
      topic: this.resolveAiTopic(),
      language: this.generatorForm.language,
      platforms: [],
      accountIds: [],
      targetProfiles: this.selectedTargetProfileIds,
      publishNow: this.photoBatchForm.publishNow
    }).subscribe({
      next: (result) => {
        this.photoBatchBusy = false;
        this.photoBatchError = !result.success;
        this.photoBatchMessage = result.message;
        this.ui.pushActionResult(result);
        if (result.success) {
          this.selectedPhotoFiles = [];
        }
      },
      error: (error) => {
        this.photoBatchBusy = false;
        this.photoBatchError = true;
        this.photoBatchMessage = error?.error?.message || error?.message || 'Photo batch request failed.';
      }
    });
  }

  protected createManualPost(): void {
    if (!this.ensureTargetProfiles()) {
      return;
    }
    this.dashboardService.createQueuePost({
      ...this.ui.sanitizeStringFields(this.newPostForm),
      topic: this.resolveManualTopic(),
      platforms: [],
      accountIds: [],
      targetProfiles: this.selectedTargetProfileIds
    }).subscribe(() => {
      this.ui.pushActionResult({
        success: true,
        command: 'create-queue-post',
        message: 'New post added to queue.'
      });
      this.newPostForm = {
        topic: '',
        text: '',
        visualHint: '',
        imageUrl: '',
        imageSourcePage: '',
        status: 'ready',
        language: 'uk'
      };
    });
  }

  private resolveAiTopic(): string {
    const template = this.promptTemplates.find((item) => item.id === this.selectedPromptTemplateId);
    return this.compactTopic(template?.topic || this.generatorForm.prompt || 'AI generated posts');
  }

  private resolveManualTopic(): string {
    return this.compactTopic(this.newPostForm.topic || this.newPostForm.text || 'Manual post');
  }

  private compactTopic(value: string): string {
    const normalized = value.replace(/\s+/g, ' ').trim();
    if (!normalized) {
      return 'Untitled post';
    }
    return normalized.length > 80 ? `${normalized.slice(0, 77)}...` : normalized;
  }

  private ensureTargetProfiles(): boolean {
    if (this.selectedTargetProfileIds.length > 0) {
      this.targetProfileError = '';
      return true;
    }
    this.targetProfileError = 'Choose at least one profile: X or Threads.';
    return false;
  }

  private handleGenerationResult(result: GeneratePromptResponse): void {
    this.generatedPosts = result.posts;
    this.generationResultMessage = result.message;
    this.ui.pushActionResult({
      success: true,
      command: 'generate',
      message: result.message
    });
  }

}
