import { AsyncPipe, NgFor, NgIf } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GeneratePromptResponse } from '../models/dashboard.models';
import { DashboardService } from '../services/dashboard.service';
import { AdminUiStateService } from '../services/admin-ui-state.service';

interface PromptTemplate {
  id: string;
  title: string;
  description: string;
  prompt: string;
  topic: string;
  tone: string;
}

@Component({
  selector: 'app-create-page',
  standalone: true,
  imports: [AsyncPipe, NgFor, NgIf, FormsModule],
  template: `
    <section class="page" *ngIf="ui.vm$ | async as vm">
      <header class="page-head d-flex flex-wrap justify-content-between align-items-end gap-2">
        <div>
          <p class="eyebrow">Create</p>
          <h1>Posts</h1>
        </div>
        <p class="muted mb-1">Pick exact profiles, then generate or add posts.</p>
      </header>

      <article class="panel">
        <div class="section-head">
          <h2>Profiles</h2>
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
            <span class="avatar" [class.x]="profile.platform === 'x'" [class.threads]="profile.platform === 'threads'">
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

      <section class="create-grid">
        <article class="panel photo-panel">
          <div class="section-head">
            <h2>Photo Batch</h2>
            <label class="form-check form-switch m-0">
              <input class="form-check-input" [(ngModel)]="photoBatchForm.publishNow" type="checkbox" />
              <span class="form-check-label">Publish now</span>
            </label>
          </div>

          <div class="row g-2">
            <div class="col-md-4">
              <label class="file-drop">
                <span>Photos</span>
                <input class="form-control form-control-sm" type="file" accept="image/*" multiple (change)="selectPhotoBatch($event)" />
                <strong>{{ selectedPhotoFiles.length || 'No' }} selected</strong>
              </label>
            </div>
            <div class="col-md-8">
              <label class="field">
                <span>Caption prompt</span>
                <textarea class="form-control" [(ngModel)]="photoBatchForm.prompt" rows="4"></textarea>
              </label>
            </div>
            <div class="col-md-5">
              <label class="field">
                <span>Topic</span>
                <input class="form-control form-control-sm" [(ngModel)]="photoBatchForm.topic" />
              </label>
            </div>
            <div class="col-md-3">
              <label class="field">
                <span>Language</span>
                <input class="form-control form-control-sm" [(ngModel)]="photoBatchForm.language" />
              </label>
            </div>
            <div class="col-md-4">
              <label class="field">
                <span>Tone</span>
                <input class="form-control form-control-sm" [(ngModel)]="photoBatchForm.tone" />
              </label>
            </div>
          </div>

          <div class="actions">
            <button class="btn btn-primary btn-sm" type="button" [disabled]="photoBatchBusy || selectedPhotoFiles.length === 0" (click)="createPhotoBatch()">
              {{ photoBatchBusy ? 'Processing...' : photoBatchForm.publishNow ? 'Generate and publish' : 'Generate to queue' }}
            </button>
          </div>
          <p class="feedback" *ngIf="photoBatchMessage" [class.error]="photoBatchError">{{ photoBatchMessage }}</p>
        </article>

        <article class="panel">
          <div class="section-head">
            <h2>Generate</h2>
            <button type="button" class="btn btn-outline-secondary btn-sm" (click)="showGeneratorDetails = !showGeneratorDetails">
              {{ showGeneratorDetails ? 'Less' : 'More' }}
            </button>
          </div>

          <div class="template-strip">
            <label
              class="template-chip"
              *ngFor="let template of promptTemplates"
              [class.active]="isPromptTemplateSelected(template.id)"
            >
              <input
                type="checkbox"
                [checked]="isPromptTemplateSelected(template.id)"
                (change)="togglePromptTemplate(template.id, $event)"
              />
              <span>{{ template.title }}</span>
            </label>
          </div>
          <div class="d-flex gap-2 flex-wrap mt-2">
            <button type="button" class="btn btn-outline-primary btn-sm" [disabled]="selectedPromptTemplateIds.length === 0" (click)="applySelectedPromptTemplates()">Apply</button>
            <button type="button" class="btn btn-outline-secondary btn-sm" [disabled]="selectedPromptTemplateIds.length === 0" (click)="clearPromptSelection()">Clear</button>
          </div>

          <label class="field mt-3">
            <span>Main prompt</span>
            <textarea class="form-control" [(ngModel)]="generatorForm.prompt" rows="5"></textarea>
          </label>

          <div class="row g-2 mt-1">
            <div class="col-md-7">
              <label class="field">
                <span>Topic</span>
                <input class="form-control form-control-sm" [(ngModel)]="generatorForm.topic" />
              </label>
            </div>
            <div class="col-md-2">
              <label class="field">
                <span>Count</span>
                <input class="form-control form-control-sm" [(ngModel)]="generatorForm.count" type="number" min="1" max="12" />
              </label>
            </div>
            <div class="col-md-3 d-flex align-items-end">
              <label class="form-check mb-2">
                <input class="form-check-input" [(ngModel)]="generatorForm.saveToQueue" type="checkbox" />
                <span class="form-check-label">Save</span>
              </label>
            </div>
          </div>

          <div class="advanced row g-2" *ngIf="showGeneratorDetails">
            <div class="col-md-5">
              <label class="field">
                <span>Language</span>
                <input class="form-control form-control-sm" [(ngModel)]="generatorForm.language" />
              </label>
            </div>
            <div class="col-md-7">
              <label class="field">
                <span>Tone</span>
                <input class="form-control form-control-sm" [(ngModel)]="generatorForm.tone" />
              </label>
            </div>
          </div>

          <div class="actions">
            <button type="button" class="btn btn-primary btn-sm" (click)="generateFromPrompt()">Generate</button>
          </div>
          <p class="feedback" *ngIf="generationResultMessage">{{ generationResultMessage }}</p>
          <ol class="generated-results" *ngIf="generatedPosts.length > 0">
            <li *ngFor="let post of generatedPosts">{{ post }}</li>
          </ol>
        </article>

        <article class="panel">
          <div class="section-head">
            <h2>Manual</h2>
            <button type="button" class="btn btn-outline-secondary btn-sm" (click)="showManualDetails = !showManualDetails">
              {{ showManualDetails ? 'Less' : 'More' }}
            </button>
          </div>

          <label class="field">
            <span>Text</span>
            <textarea class="form-control" [(ngModel)]="newPostForm.text" rows="5"></textarea>
          </label>
          <label class="field mt-2">
            <span>Topic</span>
            <input class="form-control form-control-sm" [(ngModel)]="newPostForm.topic" />
          </label>

          <div class="advanced row g-2" *ngIf="showManualDetails">
            <div class="col-md-4">
              <label class="field">
                <span>Status</span>
                <input class="form-control form-control-sm" [(ngModel)]="newPostForm.status" />
              </label>
            </div>
            <div class="col-md-4">
              <label class="field">
                <span>Language</span>
                <input class="form-control form-control-sm" [(ngModel)]="newPostForm.language" />
              </label>
            </div>
            <div class="col-md-4">
              <label class="field">
                <span>Tone</span>
                <input class="form-control form-control-sm" [(ngModel)]="newPostForm.tone" />
              </label>
            </div>
            <div class="col-12">
              <label class="field">
                <span>Image URL</span>
                <input class="form-control form-control-sm" [(ngModel)]="newPostForm.imageUrl" />
              </label>
            </div>
          </div>

          <div class="actions">
            <button type="button" class="btn btn-primary btn-sm" (click)="createManualPost()">Add to queue</button>
          </div>
          <p class="feedback" *ngIf="ui.actionResult$ | async as result" [class.error]="!result.success">
            <strong>{{ result.command }}</strong>
            <span>{{ result.message }}</span>
          </p>
        </article>
      </section>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .page { display: grid; gap: 12px; }
    .page-head h1 { margin: 0; font-size: 28px; line-height: 1.05; }
    .eyebrow, .field span, .file-drop span {
      margin: 0 0 4px;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: #64748b;
      font: 700 11px/1.2 "Segoe UI", sans-serif;
    }
    .muted { color: #64748b; font: 500 13px/1.4 "Segoe UI", sans-serif; }
    .panel {
      padding: 14px;
      background: #fff;
      border: 1px solid #dde3ea;
      border-radius: 12px;
      box-shadow: 0 8px 22px rgba(15, 23, 42, 0.05);
    }
    .section-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 10px; }
    .section-head h2 { margin: 0; font-size: 17px; font-weight: 800; }
    .create-grid { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); gap: 12px; align-items: start; }
    .photo-panel { grid-column: 1 / -1; }
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
    .field { display: grid; gap: 4px; }
    .form-control, .form-select { border-radius: 8px; }
    textarea.form-control { resize: vertical; line-height: 1.45; }
    .file-drop {
      min-height: 100%;
      display: grid;
      align-content: center;
      gap: 8px;
      padding: 12px;
      border: 1px dashed #b7c1cc;
      border-radius: 10px;
      background: #f8fafc;
    }
    .file-drop strong { color: #17212b; font: 800 14px/1.2 "Segoe UI", sans-serif; }
    .template-strip { display: flex; gap: 6px; flex-wrap: wrap; }
    .template-chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 9px;
      border: 1px solid #dde3ea;
      border-radius: 999px;
      background: #f8fafc;
      color: #334155;
      font: 700 12px/1 "Segoe UI", sans-serif;
      cursor: pointer;
    }
    .template-chip.active { border-color: #0d6efd; background: #edf5ff; color: #0b5ed7; }
    .template-chip input { width: 14px; height: 14px; }
    .advanced { margin-top: 8px; padding-top: 8px; border-top: 1px dashed #dde3ea; }
    .actions { margin-top: 10px; display: flex; gap: 8px; justify-content: flex-end; flex-wrap: wrap; }
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
    @media (max-width: 1000px) { .create-grid { grid-template-columns: 1fr; } }
  `]
})
export class CreatePageComponent {
  private readonly promptTemplateStorageKey = 'behind-the-smile.promptTemplates';
  protected readonly ui = inject(AdminUiStateService);
  private readonly dashboardService = inject(DashboardService);

  protected showGeneratorDetails = false;
  protected showManualDetails = false;
  protected photoBatchBusy = false;
  protected photoBatchMessage = '';
  protected photoBatchError = false;
  protected generatedPosts: string[] = [];
  protected generationResultMessage = '';
  protected selectedPhotoFiles: File[] = [];
  protected selectedTargetProfileIds: string[] = [];
  protected targetProfileError = '';
  protected selectedPromptTemplateIds: string[] = ['quiet-vlog'];
  protected newTemplateTitle = '';
  protected promptTemplates: PromptTemplate[] = [];
  private readonly defaultPromptTemplates: PromptTemplate[] = [
    {
      id: 'quiet-vlog',
      title: 'Quiet vlog',
      description: 'Soft diary-like posts.',
      prompt: 'Write in Ukrainian. Make the posts feel like a quiet voice-over from a personal vlog: short sentences, small everyday details, honest emotion, and a little silence between thoughts. Avoid slogans, generic awareness language, and unnecessary brand mentions.',
      topic: 'Personal vlog reflections on therapy, lyrics, and difficult days',
      tone: 'warm, honest, cinematic, diary-like, human'
    },
    {
      id: 'therapy-reflection',
      title: 'Therapy',
      description: 'Grounded healing reflections.',
      prompt: 'Write in Ukrainian. Create posts that sound like a calm therapy reflection after a difficult but important day. Keep the language simple and human. Avoid clinical advice, motivational cliches, and dramatic wording.',
      topic: 'Therapy reflections, boundaries, and emotional recovery',
      tone: 'calm, caring, honest, grounded'
    },
    {
      id: 'music-lyrics',
      title: 'Music',
      description: 'Song-feeling posts without quotes.',
      prompt: 'Write in Ukrainian. Make each post feel inspired by music and lyrics, but do not quote any real lyrics. Keep it short, visual, and personal.',
      topic: 'Music, lyric-like feelings, and late-night reflections',
      tone: 'cinematic, intimate, reflective, simple'
    }
  ];
  protected generatorForm = {
    prompt: this.defaultPromptTemplates[0].prompt,
    topic: this.defaultPromptTemplates[0].topic,
    tone: this.defaultPromptTemplates[0].tone,
    language: 'uk',
    count: 3,
    saveToQueue: true
  };
  protected photoBatchForm = {
    prompt: 'Напиши українські підписи, які відштовхуються від деталей фото. Тон: живий, короткий, ненавʼязливий, ніби кадр з особистого влогу. Без хештегів.',
    topic: 'Uploaded photo batch',
    tone: 'warm, honest, cinematic, human',
    language: 'uk',
    publishNow: false
  };
  protected newPostForm = {
    topic: '',
    text: '',
    visualHint: '',
    imageUrl: '',
    imageSourcePage: '',
    status: 'ready',
    language: 'uk',
    tone: 'warm, honest, cinematic, human'
  };

  constructor() {
    this.promptTemplates = this.loadPromptTemplates();
    this.selectedPromptTemplateIds = this.promptTemplates[0]?.id ? [this.promptTemplates[0].id] : [];
  }

  protected isPromptTemplateSelected(templateId: string): boolean {
    return this.selectedPromptTemplateIds.includes(templateId);
  }

  protected togglePromptTemplate(templateId: string, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    if (checked && !this.selectedPromptTemplateIds.includes(templateId)) {
      this.selectedPromptTemplateIds = [...this.selectedPromptTemplateIds, templateId];
      return;
    }
    if (!checked) {
      this.selectedPromptTemplateIds = this.selectedPromptTemplateIds.filter((id) => id !== templateId);
    }
  }

  protected clearPromptSelection(): void {
    this.selectedPromptTemplateIds = [];
  }

  protected applySelectedPromptTemplates(): void {
    const templates = this.selectedPromptTemplates();
    if (templates.length === 0) {
      this.generationResultMessage = 'Select at least one prompt template first.';
      return;
    }
    this.generatorForm = {
      ...this.generatorForm,
      prompt: templates.map((template) => `${template.title}:\n${template.prompt}`).join('\n\n'),
      topic: this.uniqueJoined(templates.map((template) => template.topic)),
      tone: this.uniqueJoined(templates.map((template) => template.tone))
    };
    this.generationResultMessage = `${templates.length} prompt template(s) applied.`;
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

  protected generateFromPrompt(): void {
    if (!this.ensureTargetProfiles()) {
      return;
    }
    this.dashboardService.generatePrompt({
      ...this.generatorForm,
      platforms: [],
      accountIds: [],
      targetProfiles: this.selectedTargetProfileIds
    }).subscribe((result) => this.handleGenerationResult(result));
  }

  protected selectPhotoBatch(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedPhotoFiles = Array.from(input.files ?? []);
    this.photoBatchMessage = this.selectedPhotoFiles.length ? `${this.selectedPhotoFiles.length} photo(s) ready.` : '';
    this.photoBatchError = false;
  }

  protected createPhotoBatch(): void {
    if (!this.ensureTargetProfiles()) {
      return;
    }
    if (this.selectedPhotoFiles.length === 0) {
      this.photoBatchMessage = 'Choose one or more photos first.';
      this.photoBatchError = true;
      return;
    }
    this.photoBatchBusy = true;
    this.photoBatchError = false;
    this.photoBatchMessage = 'Generating captions...';
    this.dashboardService.createPhotoBatch({
      photos: this.selectedPhotoFiles,
      prompt: this.photoBatchForm.prompt,
      topic: this.photoBatchForm.topic,
      tone: this.photoBatchForm.tone,
      language: this.photoBatchForm.language,
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
        language: 'uk',
        tone: 'warm, honest, cinematic, human'
      };
    });
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

  private selectedPromptTemplates(): PromptTemplate[] {
    return this.promptTemplates.filter((template) => this.selectedPromptTemplateIds.includes(template.id));
  }

  private uniqueJoined(values: string[]): string {
    return Array.from(new Set(values.flatMap((value) => value.split(',')).map((value) => value.trim()).filter(Boolean))).join(', ');
  }

  private loadPromptTemplates(): PromptTemplate[] {
    try {
      const stored = window.localStorage.getItem(this.promptTemplateStorageKey);
      if (!stored) {
        return this.defaultPromptTemplates;
      }
      const parsed = JSON.parse(stored);
      if (!Array.isArray(parsed)) {
        return this.defaultPromptTemplates;
      }
      const templates = parsed.filter((template): template is PromptTemplate =>
        typeof template?.id === 'string'
        && typeof template?.title === 'string'
        && typeof template?.description === 'string'
        && typeof template?.prompt === 'string'
        && typeof template?.topic === 'string'
        && typeof template?.tone === 'string'
      );
      return templates.length > 0 ? templates : this.defaultPromptTemplates;
    } catch {
      return this.defaultPromptTemplates;
    }
  }
}
