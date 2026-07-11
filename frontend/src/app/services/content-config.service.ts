import { Injectable } from '@angular/core';

export interface PromptPreset {
  id: string;
  title: string;
  description: string;
  prompt: string;
  topic: string;
}

const PRESETS_STORAGE_KEY = 'behind-the-smile.promptTemplates';

@Injectable({
  providedIn: 'root'
})
export class ContentConfigService {
  readonly defaultPresets: PromptPreset[] = [
    {
      id: 'quiet-vlog',
      title: 'Quiet personal vlog',
      description: 'Soft diary-like posts.',
      prompt:
        'Write in Ukrainian. Make the posts feel like a quiet voice-over from a personal vlog: short sentences, small everyday details, honest emotion, and a little silence between thoughts. Avoid slogans, generic awareness language, and unnecessary brand mentions.',
      topic: 'Personal vlog reflections on therapy, lyrics, and difficult days'
    },
    {
      id: 'therapy-reflection',
      title: 'Therapy reflection',
      description: 'Grounded healing reflections.',
      prompt:
        'Write in Ukrainian. Create posts that sound like a calm therapy reflection after a difficult but important day. Keep the language simple and human. Avoid clinical advice, motivational cliches, and dramatic wording.',
      topic: 'Therapy reflections, boundaries, and emotional recovery'
    },
    {
      id: 'music-lyrics',
      title: 'Music and lyrics',
      description: 'Song-feeling posts without quotes.',
      prompt:
        'Write in Ukrainian. Make each post feel inspired by music and lyrics, but do not quote any real lyrics. Keep it short, visual, and personal.',
      topic: 'Music, lyric-like feelings, and late-night reflections'
    }
  ];

  loadPresets(): PromptPreset[] {
    try {
      const stored = window.localStorage.getItem(PRESETS_STORAGE_KEY);
      if (!stored) {
        return this.defaultPresets;
      }
      const parsed = JSON.parse(stored);
      if (!Array.isArray(parsed)) {
        return this.defaultPresets;
      }
      const presets = parsed.filter((preset): preset is PromptPreset =>
        typeof preset?.id === 'string'
        && typeof preset?.title === 'string'
        && typeof preset?.description === 'string'
        && typeof preset?.prompt === 'string'
        && typeof preset?.topic === 'string'
      );
      return presets.length > 0 ? presets : this.defaultPresets;
    } catch {
      return this.defaultPresets;
    }
  }

  savePresets(presets: PromptPreset[]): void {
    window.localStorage.setItem(PRESETS_STORAGE_KEY, JSON.stringify(presets));
  }
}
