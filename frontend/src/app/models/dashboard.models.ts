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
  automationMode?: string;
  lastError?: string;
  logTail?: string;
  url?: string;
  pageUrl?: string;
  title?: string;
  percent?: number;
  durationSeconds?: number;
  currentTime?: number;
  videoPresent?: boolean;
  paused?: boolean;
  readyState?: number;
  muted?: boolean;
  volume?: number;
  browser?: string;
  accountId?: string;
}

export interface ChromeProfilesStatus {
  directory: string;
  script: string;
  startProfileScript?: string;
  envFile?: string;
  directoryExists: boolean;
  scriptExists: boolean;
  startProfileScriptExists?: boolean;
  envFileExists?: boolean;
  profiles?: ChromeProfileSummary[];
  logExists: boolean;
  lastStartedAt: string;
  logTail: string;
  minDelaySeconds?: number;
  maxDelaySeconds?: number;
  profileCount?: number;
  profileNames?: string[];
  url?: string;
  message?: string;
}

export interface ChromeProfileSummary {
  name: string;
  label?: string;
  loginStatus?: string;
  loggedIn?: boolean | string;
  proxy: string;
  upstreamProxy: string;
}

export interface ChromeProfilesLaunchRequest {
  minDelaySeconds: number;
  maxDelaySeconds: number;
  profileCount?: number;
  url?: string;
  profileNames?: string[];
}

export interface ChromeProfilesUrlCheckRequest {
  url: string;
}

export interface ChromeProfilesUrlCheckStatus {
  url: string;
  okCount: number;
  totalCount: number;
  results: ChromeProfilesUrlCheckResult[];
}

export interface ChromeProfilesUrlCheckResult {
  name: string;
  proxy: string;
  ok: boolean;
  status: string;
  statusCode: number;
  location: string;
  redirectMarker: string;
  reason: string;
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
