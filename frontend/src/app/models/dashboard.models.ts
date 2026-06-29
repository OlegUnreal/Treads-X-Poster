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
  activeAccountId: string;
  activeAccountLabel: string;
  xAccountLabel: string;
  xModeLabel: string;
  threadsAccountLabel: string;
  availableAccounts: PublisherAccountOption[];
}

export interface PublisherAccountOption {
  id: string;
  label: string;
  xAccountLabel: string;
  xModeLabel: string;
  threadsAccountLabel: string;
  xAvatarUrl?: string;
  threadsAvatarUrl?: string;
}

export interface PublishingProfile {
  id: string;
  accountId: string;
  platform: 'x' | 'threads';
  name: string;
  subtitle: string;
  avatarUrl?: string;
}

export interface AccountSelectionResponse {
  activeAccountId: string;
  accounts: PublisherAccountOption[];
}

export interface QueuePost {
  id: string;
  accountId?: string;
  accountLabel?: string;
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
  accountIds?: string[];
  targetProfiles?: string[];
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
  accountIds?: string[];
  targetProfiles?: string[];
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

export interface YoutubePlaybackRequest {
  url: string;
  percent: number;
}

export interface YoutubePlaybackStatus {
  status: string;
  url?: string;
  percent?: number;
  durationSeconds?: number;
  browser?: string;
  accountId?: string;
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
