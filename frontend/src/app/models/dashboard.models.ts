export interface RunSummary {
  queueReady: number;
  threadsReady: number;
  xReady: number;
  postingStatus: 'running' | 'stopped' | 'degraded';
  lastDailyMessage: string;
  lastThreadsMessage: string;
  publisherAccounts: PublisherAccountSummary;
  jobStatus: PostingJobStatus | null;
}

export interface PublisherAccountSummary {
  xAccountLabel: string;
  xModeLabel: string;
  threadsAccountLabel: string;
}

export interface QueuePost {
  id: string;
  topic: string;
  text: string;
  visualHint?: string;
  imageUrl?: string;
  imageSourcePage?: string;
  imageAttribution?: string;
  imageLicense?: string;
  status: string;
  platforms: string[];
  createdAt: string;
  language?: string;
  tone?: string;
  published?: Record<string, {
    at: string;
    result: unknown;
  }>;
}

export interface ActionResult {
  success: boolean;
  command: string;
  message: string;
}

export interface QueuePostUpsertRequest {
  topic: string;
  text: string;
  visualHint?: string;
  imageUrl?: string;
  imageSourcePage?: string;
  imageAttribution?: string;
  imageLicense?: string;
  status: string;
  platforms: string[];
  language: string;
  tone: string;
}

export interface GeneratePromptRequest {
  prompt: string;
  topic: string;
  tone: string;
  language: string;
  count: number;
  platforms: string[];
  saveToQueue: boolean;
}

export interface GeneratePromptResponse {
  posts: string[];
  savedToQueue: boolean;
  message: string;
}

export interface PostingJobRequest {
  intervalHours: number;
  threadsPerRun: number;
  xPerRun: number;
  minimumReady: number;
  randomizeUpToHour: boolean;
}

export interface BrowserXPublishRequest {
  queuePostId: string | null;
  text: string;
  imageUrl?: string | null;
  markPublished: boolean;
}

export interface PostingJobStatus {
  running: boolean;
  intervalHours: number | null;
  threadsPerRun: number | null;
  xPerRun: number | null;
  minimumReady: number | null;
  randomizeUpToHour: boolean | null;
  startedAt: string | null;
  lastHeartbeatAt: string | null;
  nextRunAt: string | null;
  nextRunOffsetMinutes: number | null;
  lastRunMessage: string;
}
