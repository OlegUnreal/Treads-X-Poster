import { NgFor, NgIf } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ContentConfigService, PromptPreset } from '../services/content-config.service';

@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [NgFor, NgIf, FormsModule],
  template: `
    <section class="page">
      <header class="page-head">
        <div>
          <p class="eyebrow">Settings</p>
          <h1>Content config</h1>
        </div>
        <button class="btn btn-outline-secondary btn-sm" type="button" (click)="resetDefaults()">Reset defaults</button>
      </header>

      <section class="settings-grid">
        <article class="panel">
          <div class="section-head">
            <h2>Allowed tones</h2>
            <span class="badge text-bg-light">{{ tones.length }}</span>
          </div>

          <div class="add-row">
            <input class="form-control form-control-sm" [(ngModel)]="newTone" placeholder="warm, short, personal" />
            <button class="btn btn-primary btn-sm" type="button" (click)="addTone()">Add</button>
          </div>

          <div class="tone-list">
            <div class="tone-row" *ngFor="let tone of tones">
              <span>{{ tone }}</span>
              <button class="btn btn-outline-danger btn-sm" type="button" [disabled]="tones.length <= 1" (click)="removeTone(tone)">Remove</button>
            </div>
          </div>
        </article>

        <article class="panel">
          <div class="section-head">
            <h2>Presets</h2>
            <span class="badge text-bg-light">{{ presets.length }}</span>
          </div>

          <div class="preset-list">
            <button
              class="preset-row"
              type="button"
              *ngFor="let preset of presets"
              [class.active]="preset.id === editingPreset.id"
              (click)="editPreset(preset)"
            >
              <strong>{{ preset.title }}</strong>
              <small>{{ preset.tone }}</small>
            </button>
          </div>
        </article>

        <article class="panel editor-panel">
          <div class="section-head">
            <h2>{{ editingPreset.id ? 'Edit preset' : 'New preset' }}</h2>
            <button class="btn btn-outline-secondary btn-sm" type="button" (click)="newPreset()">New</button>
          </div>

          <div class="form-grid">
            <label class="field">
              <span>Name</span>
              <input class="form-control form-control-sm" [(ngModel)]="editingPreset.title" />
            </label>
            <label class="field">
              <span>Tone</span>
              <select class="form-select form-select-sm" [(ngModel)]="editingPreset.tone">
                <option *ngFor="let tone of tones" [ngValue]="tone">{{ tone }}</option>
              </select>
            </label>
            <label class="field wide">
              <span>Prompt</span>
              <textarea class="form-control" [(ngModel)]="editingPreset.prompt" rows="6"></textarea>
            </label>
          </div>

          <div class="actions">
            <button class="btn btn-primary btn-sm" type="button" (click)="savePreset()">Save preset</button>
            <button class="btn btn-outline-danger btn-sm" type="button" *ngIf="editingPreset.id" [disabled]="presets.length <= 1" (click)="deletePreset()">Delete</button>
          </div>
          <p class="feedback" *ngIf="message">{{ message }}</p>
        </article>
      </section>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .page { display: grid; gap: 12px; }
    .page-head { display: flex; justify-content: space-between; align-items: end; gap: 12px; }
    .page-head h1 { margin: 0; font-size: 28px; line-height: 1.05; }
    .eyebrow, .field span {
      margin: 0 0 4px;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: #64748b;
      font: 700 11px/1.2 "Segoe UI", sans-serif;
    }
    .settings-grid { display: grid; grid-template-columns: minmax(260px, 0.8fr) minmax(260px, 0.8fr) minmax(360px, 1.4fr); gap: 12px; align-items: start; }
    .panel {
      padding: 14px;
      background: #fff;
      border: 1px solid #dde3ea;
      border-radius: 12px;
      box-shadow: 0 8px 22px rgba(15, 23, 42, 0.05);
    }
    .section-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 10px; }
    .section-head h2 { margin: 0; font-size: 17px; font-weight: 800; }
    .add-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
    .tone-list, .preset-list { display: grid; gap: 8px; margin-top: 10px; }
    .tone-row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      gap: 8px;
      padding: 8px;
      border: 1px solid #dde3ea;
      border-radius: 10px;
      background: #f8fafc;
      color: #334155;
      font: 700 13px/1.35 "Segoe UI", sans-serif;
    }
    .preset-row {
      display: grid;
      gap: 3px;
      width: 100%;
      padding: 9px 10px;
      text-align: left;
      border: 1px solid #dde3ea;
      border-radius: 10px;
      background: #f8fafc;
      color: #17212b;
    }
    .preset-row.active { border-color: #0d6efd; background: #edf5ff; }
    .preset-row strong { font: 800 13px/1.25 "Segoe UI", sans-serif; }
    .preset-row small { color: #64748b; font: 600 12px/1.3 "Segoe UI", sans-serif; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .form-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 10px; }
    .field { display: grid; gap: 4px; }
    .wide { grid-column: 1 / -1; }
    textarea.form-control { resize: vertical; line-height: 1.45; }
    .actions { display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
    .feedback { margin: 10px 0 0; padding: 8px 10px; border-radius: 8px; background: #e9f7ef; color: #146c43; font: 700 13px/1.35 "Segoe UI", sans-serif; }
    @media (max-width: 1100px) { .settings-grid { grid-template-columns: 1fr; } }
    @media (max-width: 700px) { .page-head, .add-row, .tone-row, .form-grid { grid-template-columns: 1fr; display: grid; align-items: stretch; } }
  `]
})
export class SettingsPageComponent {
  private readonly contentConfig = inject(ContentConfigService);

  protected tones = this.contentConfig.loadTones();
  protected presets = this.contentConfig.loadPresets();
  protected newTone = '';
  protected message = '';
  protected editingPreset: PromptPreset = this.clonePreset(this.presets[0] ?? this.blankPreset());

  protected addTone(): void {
    const tone = this.newTone.trim();
    if (!tone) {
      return;
    }
    this.tones = Array.from(new Set([...this.tones, tone]));
    this.contentConfig.saveTones(this.tones);
    this.newTone = '';
    this.message = 'Tone saved.';
  }

  protected removeTone(tone: string): void {
    if (this.tones.length <= 1) {
      return;
    }
    this.tones = this.tones.filter((item) => item !== tone);
    this.contentConfig.saveTones(this.tones);
    if (this.editingPreset.tone === tone) {
      this.editingPreset.tone = this.tones[0] ?? '';
    }
    this.message = 'Tone removed.';
  }

  protected editPreset(preset: PromptPreset): void {
    this.editingPreset = this.clonePreset(preset);
    this.message = '';
  }

  protected newPreset(): void {
    this.editingPreset = this.blankPreset();
    this.message = '';
  }

  protected savePreset(): void {
    const title = this.editingPreset.title.trim();
    const prompt = this.editingPreset.prompt.trim();
    if (!title || !prompt) {
      this.message = 'Name and prompt are required.';
      return;
    }

    const preset: PromptPreset = {
      ...this.editingPreset,
      id: this.editingPreset.id || `custom-${Date.now()}`,
      title,
      description: this.editingPreset.description || 'Saved from Settings.',
      prompt,
      topic: this.compactTopic(this.editingPreset.topic || prompt),
      tone: this.editingPreset.tone || this.tones[0] || ''
    };
    const exists = this.presets.some((item) => item.id === preset.id);
    this.presets = exists
      ? this.presets.map((item) => item.id === preset.id ? preset : item)
      : [...this.presets, preset];
    this.editingPreset = this.clonePreset(preset);
    this.contentConfig.savePresets(this.presets);
    this.message = 'Preset saved.';
  }

  protected deletePreset(): void {
    if (!this.editingPreset.id || this.presets.length <= 1) {
      return;
    }
    this.presets = this.presets.filter((item) => item.id !== this.editingPreset.id);
    this.contentConfig.savePresets(this.presets);
    this.editingPreset = this.clonePreset(this.presets[0]);
    this.message = 'Preset deleted.';
  }

  protected resetDefaults(): void {
    this.tones = [...this.contentConfig.defaultTones];
    this.presets = this.contentConfig.defaultPresets.map((preset) => this.clonePreset(preset));
    this.contentConfig.saveTones(this.tones);
    this.contentConfig.savePresets(this.presets);
    this.editingPreset = this.clonePreset(this.presets[0]);
    this.message = 'Defaults restored.';
  }

  private blankPreset(): PromptPreset {
    return {
      id: '',
      title: '',
      description: 'Saved from Settings.',
      prompt: '',
      topic: '',
      tone: this.tones[0] ?? ''
    };
  }

  private clonePreset(preset: PromptPreset): PromptPreset {
    return { ...preset };
  }

  private compactTopic(value: string): string {
    const normalized = value.replace(/\s+/g, ' ').trim();
    if (!normalized) {
      return 'Untitled preset';
    }
    return normalized.length > 80 ? `${normalized.slice(0, 77)}...` : normalized;
  }
}
