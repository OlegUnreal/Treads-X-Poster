import { AsyncPipe, NgFor, NgIf } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GeneratePromptResponse } from '../models/dashboard.models';
import { DashboardService } from '../services/dashboard.service';
import { AdminUiStateService } from '../services/admin-ui-state.service';

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
    @media (max-width: 900px) { .grid, .form-grid { grid-template-columns: 1fr; } }
  `]
})
export class CreatePageComponent {
  protected readonly ui = inject(AdminUiStateService);
  private readonly dashboardService = inject(DashboardService);

  protected showGeneratorDetails = false;
  protected showManualDetails = false;
  protected generatorPlatforms = 'x, threads';
  protected generatedPosts: string[] = [];
  protected generationResultMessage = '';
  protected newPostPlatforms = 'x, threads';
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
}
