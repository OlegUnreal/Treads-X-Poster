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
    <section class="page">
      <header class="page-head">
        <div>
          <p class="eyebrow">Create Posts</p>
          <h1>Create</h1>
          <p>Generation and manual drafting live here, separate from publishing.</p>
        </div>
      </header>

      <section class="grid">
        <article class="panel">
          <div class="panel-head">
            <h2>Generate With Prompt</h2>
            <p>Ask OpenAI for a few fresh post ideas in your current voice.</p>
          </div>

          <div class="prompt-tools">
            <button type="button" class="ghost" [disabled]="selectedPromptTemplateIds.length === 0" (click)="applySelectedPromptTemplates()">
              Apply Selected
            </button>
            <button type="button" class="ghost" [disabled]="selectedPromptTemplateIds.length === 0" (click)="clearPromptSelection()">
              Clear Selection
            </button>
          </div>
          <p class="template-note">
            {{ selectedPromptTemplateIds.length }} selected from {{ promptTemplates.length }} prompt templates.
          </p>
          <ul class="template-list">
            <li *ngFor="let template of promptTemplates">
              <label class="template-row" [class.active]="isPromptTemplateSelected(template.id)">
                <input
                  type="checkbox"
                  [checked]="isPromptTemplateSelected(template.id)"
                  (change)="togglePromptTemplate(template.id, $event)"
                />
                <span class="template-copy">
                  <strong>{{ template.title }}</strong>
                  <span>{{ template.topic }}</span>
                </span>
              </label>
            </li>
          </ul>
          <div class="template-manager">
            <label>
              <span>New Template Name</span>
              <input [(ngModel)]="newTemplateTitle" placeholder="Example: Morning note" />
            </label>
            <button type="button" class="ghost" (click)="saveCurrentPromptTemplate()">Add Current</button>
            <button type="button" class="danger" [disabled]="selectedPromptTemplateIds.length === 0 || promptTemplates.length <= selectedPromptTemplateIds.length" (click)="deleteSelectedPromptTemplates()">
              Delete Selected
            </button>
          </div>

          <div class="focus-block">
            <label class="stacked">
              <span>Main Prompt</span>
              <textarea [(ngModel)]="generatorForm.prompt" rows="8"></textarea>
            </label>
          </div>

          <div class="form-grid compact">
            <label>
              <span>Topic</span>
              <input [(ngModel)]="generatorForm.topic" />
            </label>
            <label>
              <span>Count</span>
              <input [(ngModel)]="generatorForm.count" type="number" min="1" max="12" />
            </label>
          </div>

          <div class="actions split">
            <button type="button" (click)="generateFromPrompt()">Generate With OpenAI</button>
            <button type="button" class="ghost" (click)="showGeneratorDetails = !showGeneratorDetails">
              {{ showGeneratorDetails ? 'Hide Advanced Options' : 'Show Advanced Options' }}
            </button>
          </div>

          <div class="advanced" *ngIf="showGeneratorDetails">
            <div class="form-grid">
              <label>
                <span>Tone</span>
                <input [(ngModel)]="generatorForm.tone" />
              </label>
              <label>
                <span>Language</span>
                <input [(ngModel)]="generatorForm.language" />
              </label>
              <label class="wide">
                <span>Platforms (comma separated)</span>
                <input [(ngModel)]="generatorPlatforms" />
              </label>
              <label class="wide checkbox">
                <input [(ngModel)]="generatorForm.saveToQueue" type="checkbox" />
                <span>Save generated posts directly to queue</span>
              </label>
            </div>
          </div>

          <p class="feedback" *ngIf="generationResultMessage">{{ generationResultMessage }}</p>
          <div class="generated-results" *ngIf="generatedPosts.length > 0">
            <h3>Generated Variants</h3>
            <ol>
              <li *ngFor="let post of generatedPosts">{{ post }}</li>
            </ol>
          </div>
        </article>

        <article class="panel">
          <div class="panel-head">
            <h2>Add Manual Post</h2>
            <p>Use this only when you already know exactly what you want to add.</p>
          </div>

          <div class="focus-block">
            <label class="stacked">
              <span>Text</span>
              <textarea [(ngModel)]="newPostForm.text" rows="7"></textarea>
            </label>
          </div>

          <div class="form-grid compact">
            <label>
              <span>Topic</span>
              <input [(ngModel)]="newPostForm.topic" />
            </label>
            <label>
              <span>Platforms</span>
              <input [(ngModel)]="newPostPlatforms" />
            </label>
          </div>

          <div class="actions split">
            <button type="button" (click)="createManualPost()">Add To Queue</button>
            <button type="button" class="ghost" (click)="showManualDetails = !showManualDetails">
              {{ showManualDetails ? 'Hide Advanced Options' : 'Show Advanced Options' }}
            </button>
          </div>

          <div class="advanced" *ngIf="showManualDetails">
            <div class="form-grid">
              <label>
                <span>Status</span>
                <input [(ngModel)]="newPostForm.status" />
              </label>
              <label>
                <span>Language</span>
                <input [(ngModel)]="newPostForm.language" />
              </label>
              <label>
                <span>Tone</span>
                <input [(ngModel)]="newPostForm.tone" />
              </label>
              <label>
                <span>Real Photo Idea</span>
                <input [(ngModel)]="newPostForm.visualHint" />
              </label>
              <label class="wide">
                <span>Image URL</span>
                <input [(ngModel)]="newPostForm.imageUrl" />
              </label>
              <label class="wide">
                <span>Source Page</span>
                <input [(ngModel)]="newPostForm.imageSourcePage" />
              </label>
            </div>
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
    .page { display: grid; gap: 24px; }
    .page-head p, .panel-head p { color: #52606d; font: 500 16px/1.6 "Segoe UI", sans-serif; margin: 10px 0 0; }
    .eyebrow { margin: 0 0 10px; text-transform: uppercase; letter-spacing: 0.14em; font: 700 12px/1.2 "Segoe UI", sans-serif; color: #8a5a24; }
    h1 { margin: 0; font-size: clamp(2.1rem, 4vw, 3.4rem); }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
    .panel {
      padding: 24px;
      background: rgba(255,255,255,0.78);
      border: 1px solid rgba(31,41,51,0.08);
      border-radius: 24px;
      box-shadow: 0 24px 50px rgba(69,58,42,0.12);
    }
    .panel-head h2 { margin: 0; font-size: 28px; }
    .prompt-tools {
      margin-top: 18px;
      display: grid;
      grid-template-columns: max-content max-content;
      gap: 12px;
      align-items: end;
      font-family: "Segoe UI", sans-serif;
    }
    .prompt-tools label, .template-manager label { display: grid; gap: 8px; }
    .prompt-tools span, .template-manager span {
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: #52606d;
      font-family: "Segoe UI", sans-serif;
    }
    .prompt-tools select, .template-manager input {
      width: 100%;
      border: 1px solid rgba(31,41,51,0.12);
      border-radius: 14px;
      padding: 12px 14px;
      background: rgba(255,255,255,0.92);
      color: #243b53;
      font: 700 14px/1.5 "Segoe UI", sans-serif;
    }
    .template-list {
      list-style: none;
      margin: 14px 0 0;
      padding: 0;
      display: grid;
      gap: 8px;
    }
    .template-row {
      width: 100%;
      display: flex;
      align-items: flex-start;
      gap: 4px;
      text-align: left;
      border: 1px solid rgba(31,41,51,0.10);
      border-radius: 14px;
      padding: 12px 14px;
      background: rgba(255,255,255,0.72);
      color: #243b53;
      box-shadow: none;
    }
    .template-row input {
      width: 18px;
      height: 18px;
      margin: 2px 10px 0 0;
      accent-color: #1f6feb;
      flex: 0 0 auto;
    }
    .template-row.active {
      border-color: rgba(31,111,235,0.42);
      background: rgba(31,111,235,0.08);
    }
    .template-copy {
      min-width: 0;
      display: grid;
      gap: 4px;
    }
    .template-row strong {
      font: 800 14px/1.3 "Segoe UI", sans-serif;
    }
    .template-copy > span {
      color: #52606d;
      font: 500 13px/1.5 "Segoe UI", sans-serif;
      overflow-wrap: anywhere;
    }
    .template-manager {
      margin-top: 14px;
      display: grid;
      grid-template-columns: minmax(0, 1fr) max-content max-content;
      gap: 12px;
      align-items: end;
      font-family: "Segoe UI", sans-serif;
    }
    .template-note {
      margin: 10px 0 0;
      color: #52606d;
      font: 500 14px/1.6 "Segoe UI", sans-serif;
    }
    .focus-block {
      margin-top: 18px;
      padding: 18px;
      border-radius: 20px;
      background: rgba(255,255,255,0.62);
      border: 1px solid rgba(31,41,51,0.08);
    }
    .stacked { display: grid; gap: 8px; }
    .form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 18px; }
    .form-grid.compact { margin-top: 16px; }
    .wide { grid-column: 1 / -1; }
    .form-grid label { display: grid; gap: 8px; }
    .form-grid span, .stacked span { font-size: 12px; text-transform: uppercase; letter-spacing: 0.08em; color: #52606d; font-family: "Segoe UI", sans-serif; }
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
    .stacked textarea {
      width: 100%;
      border: 1px solid rgba(31,41,51,0.12);
      border-radius: 14px;
      padding: 14px 16px;
      background: rgba(255,255,255,0.96);
      color: #243b53;
      font: 500 15px/1.6 "Segoe UI", sans-serif;
      resize: vertical;
    }
    .checkbox { display: flex !important; align-items: center; gap: 10px; }
    .checkbox input { width: auto; }
    .actions.split { display: grid; grid-template-columns: repeat(2, minmax(0, max-content)); gap: 12px; margin-top: 18px; }
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
    button.danger {
      background: rgba(154,52,18,0.10);
      color: #7c2d12;
      border: 1px solid rgba(154,52,18,0.18);
      box-shadow: none;
    }
    button:disabled {
      cursor: not-allowed;
      opacity: 0.54;
      box-shadow: none;
    }
    .advanced {
      margin-top: 18px;
      padding-top: 18px;
      border-top: 1px dashed rgba(31,41,51,0.12);
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
    .generated-results { margin-top: 18px; font-family: "Segoe UI", sans-serif; }
    .generated-results h3 { margin: 0 0 12px; font-size: 18px; }
    .generated-results ol { margin: 0; padding-left: 18px; display: grid; gap: 10px; }
    @media (max-width: 900px) { .grid, .form-grid, .prompt-tools, .template-manager { grid-template-columns: 1fr; } }
  `]
})
export class CreatePageComponent {
  private readonly promptTemplateStorageKey = 'behind-the-smile.promptTemplates';
  protected readonly ui = inject(AdminUiStateService);
  private readonly dashboardService = inject(DashboardService);

  protected showGeneratorDetails = false;
  protected showManualDetails = false;
  protected generatorPlatforms = 'x, threads';
  protected generatedPosts: string[] = [];
  protected generationResultMessage = '';
  protected newPostPlatforms = 'x, threads';
  protected selectedPromptTemplateIds: string[] = ['quiet-vlog'];
  protected newTemplateTitle = '';
  protected promptTemplates: PromptTemplate[] = [];
  private readonly defaultPromptTemplates: PromptTemplate[] = [
    {
      id: 'quiet-vlog',
      title: 'Quiet personal vlog',
      description: 'Soft, diary-like posts about real days, small details, and returning to yourself.',
      prompt: 'Write in Ukrainian. Make the posts feel like a quiet voice-over from a personal vlog: short sentences, small everyday details, honest emotion, and a little silence between thoughts. Themes can include therapy, difficult days, music, lyrics, and slowly returning to yourself. Avoid slogans, generic awareness language, and unnecessary brand mentions.',
      topic: 'Personal vlog reflections on therapy, lyrics, and difficult days',
      tone: 'warm, honest, cinematic, diary-like, human'
    },
    {
      id: 'therapy-reflection',
      title: 'Therapy reflection',
      description: 'Grounded posts about healing, boundaries, emotions, and gentle self-awareness.',
      prompt: 'Write in Ukrainian. Create posts that sound like a calm therapy reflection after a difficult but important day. Keep the language simple and human. Focus on noticing feelings, setting boundaries, recovering slowly, and allowing yourself not to be perfect. Avoid clinical advice, motivational cliches, and dramatic wording.',
      topic: 'Therapy reflections, boundaries, and emotional recovery',
      tone: 'calm, caring, honest, grounded'
    },
    {
      id: 'music-lyrics',
      title: 'Music and lyrics',
      description: 'Posts built around the feeling of a song without quoting lyrics directly.',
      prompt: 'Write in Ukrainian. Make each post feel inspired by music and lyrics, but do not quote any real lyrics. Focus on the emotional aftertaste of a song: night walks, headphones, memories, quiet sadness, hope, and the moment when one line seems to understand you. Keep it short, visual, and personal.',
      topic: 'Music, lyric-like feelings, and late-night reflections',
      tone: 'cinematic, intimate, reflective, poetic but simple'
    },
    {
      id: 'hard-day',
      title: 'Difficult day',
      description: 'Gentle posts for days when everything feels heavy but still survivable.',
      prompt: 'Write in Ukrainian. Create posts for a difficult day. The voice should be honest, not polished: tired, quiet, but still trying. Include small real-world details like tea, weather, a room, a song, or a message left unanswered. Do not force optimism. End with a small sense of breathing room.',
      topic: 'Difficult days, tiredness, and small ways to keep going',
      tone: 'honest, gentle, minimal, human'
    },
    {
      id: 'behind-the-smile',
      title: 'Behind The Smile',
      description: 'Brand-aligned posts for the project voice without sounding promotional.',
      prompt: 'Write in Ukrainian for Behind The Smile. The posts should feel like a quiet note from someone who smiles in public but is learning to be honest in private. Keep it personal, warm, and restrained. Mention the idea behind the smile only naturally, not as a slogan. Avoid sales language and generic mental health phrases.',
      topic: 'Behind The Smile: what people carry quietly',
      tone: 'warm, sincere, reflective, restrained'
    }
  ];
  protected generatorForm = {
    prompt: 'Write in Ukrainian. Make the posts feel like a quiet voice-over from a personal vlog: short sentences, small everyday details, honest emotion, and a little silence between thoughts. Themes can include therapy, difficult days, music, lyrics, and slowly returning to yourself. Avoid slogans, generic awareness language, and unnecessary brand mentions.',
    topic: 'Personal vlog reflections on therapy, lyrics, and difficult days',
    tone: 'warm, honest, cinematic, diary-like, human',
    language: 'uk',
    count: 3,
    saveToQueue: true
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

  protected saveCurrentPromptTemplate(): void {
    const title = this.newTemplateTitle.trim();
    if (!title) {
      this.generationResultMessage = 'Add a name before saving a new template.';
      return;
    }

    const template: PromptTemplate = {
      id: `custom-${Date.now()}`,
      title,
      description: 'Saved from your current prompt.',
      prompt: this.generatorForm.prompt,
      topic: this.generatorForm.topic,
      tone: this.generatorForm.tone
    };

    this.promptTemplates = [...this.promptTemplates, template];
    this.selectedPromptTemplateIds = [template.id];
    this.newTemplateTitle = '';
    this.savePromptTemplates();
    this.generationResultMessage = `${template.title} saved to the prompt list.`;
  }

  protected deleteSelectedPromptTemplates(): void {
    if (this.selectedPromptTemplateIds.length === 0) {
      this.generationResultMessage = 'Select one or more templates to delete.';
      return;
    }

    if (this.promptTemplates.length <= this.selectedPromptTemplateIds.length) {
      this.generationResultMessage = 'Keep at least one prompt template in the list.';
      return;
    }

    const deletedCount = this.selectedPromptTemplateIds.length;
    this.promptTemplates = this.promptTemplates.filter((template) => !this.selectedPromptTemplateIds.includes(template.id));
    this.selectedPromptTemplateIds = this.promptTemplates[0]?.id ? [this.promptTemplates[0].id] : [];
    this.savePromptTemplates();
    this.generationResultMessage = `${deletedCount} prompt template(s) removed from the list.`;
  }

  protected generateFromPrompt(): void {
    this.dashboardService.generatePrompt({
      ...this.generatorForm,
      platforms: this.ui.parsePlatforms(this.generatorPlatforms)
    }).subscribe((result) => this.handleGenerationResult(result));
  }

  protected createManualPost(): void {
    this.dashboardService.createQueuePost({
      ...this.ui.sanitizeStringFields(this.newPostForm),
      platforms: this.ui.parsePlatforms(this.newPostPlatforms)
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
      this.newPostPlatforms = 'x, threads';
    });
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
    return Array.from(new Set(
      values
        .flatMap((value) => value.split(','))
        .map((value) => value.trim())
        .filter(Boolean)
    )).join(', ');
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

  private savePromptTemplates(): void {
    window.localStorage.setItem(this.promptTemplateStorageKey, JSON.stringify(this.promptTemplates));
  }
}
