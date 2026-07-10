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

export interface AccountWorkspaceSummary {
  activeAccountId: string;
  totalReady: number;
  totalFailed: number;
  totalPublished: number;
  accounts: AccountWorkspaceAccount[];
}

export interface AccountWorkspaceAccount {
  id: string;
  label: string;
  prompt: string;
  language: string;
  defaultPostCount: number;
  xAccountLabel: string;
  xModeLabel: string;
  xConfigured: boolean;
  xReady: number;
  xFailed: number;
  threadsAccountLabel: string;
  threadsConfigured: boolean;
  threadsReady: number;
  threadsFailed: number;
  mediaAttached: number;
  textOnly: number;
  published: number;
}

export interface AccountConfig {
  id: string;
  label: string;
  source: 'env' | 'ui' | string;
  prompt: string;
  language: string;
  defaultPostCount: number;
  xPrompt: string;
  xLanguage: string;
  xDefaultPostCount: number;
  xAccountLabel: string;
  xAccessToken: string;
  xClientId: string;
  xClientSecret: string;
  xRedirectUri: string;
  xScopes: string;
  xApiKey: string;
  xApiSecret: string;
  xAccessTokenSecret: string;
  xRefreshToken: string;
  xPublishMode: string;
  xBrowser: string;
  xBrowserProfileDir: string;
  xBrowserHeadless: boolean;
  threadsPrompt: string;
  threadsLanguage: string;
  threadsDefaultPostCount: number;
  threadsAccountLabel: string;
  threadsAccessToken: string;
  threadsUserId: string;
  threadsAppId: string;
  threadsAppSecret: string;
  threadsRedirectUri: string;
}

export type AccountConfigRequest = Omit<AccountConfig, 'source'>;

export interface ThreadsProfileLookupResponse {
  username: string;
  name: string;
  label: string;
  profilePictureUrl: string;
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
}

export interface GeneratePromptRequest {
  prompt: string;
  topic: string;
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
  chromeFound?: boolean;
  configuredProfileCount?: number;
  loggedInProfileCount?: number;
  runningProfileCount?: number;
  profileResults?: ChromeProfileActionResult[];
}

export interface ChromeProfileSummary {
  name: string;
  label?: string;
  googleAccount?: string;
  googleAccountName?: string;
  loginStatus?: string;
  loggedIn?: boolean | string;
  proxy: string;
  upstreamProxy: string;
  proxyKey?: string;
  supportsYoutube?: boolean | string;
  supportsPornhub?: boolean | string;
  proxyCountry?: string;
  proxyCity?: string;
  timezone?: string;
  language?: string;
  windowSize?: string;
  profileDir?: string;
  running?: boolean | string;
  pid?: string;
  debugPort?: string;
  lastUrl?: string;
  lastOpenedAt?: string;
  lastMode?: string;
}

export interface ChromeProfileProxyCapabilityUpdate {
  message?: string;
  profileName?: string;
  proxyKey?: string;
  supportsYoutube?: boolean | string;
  supportsPornhub?: boolean | string;
}

export interface ChromeProfilesLaunchRequest {
  minDelaySeconds: number;
  maxDelaySeconds: number;
  profileCount?: number;
  url?: string;
  profileNames?: string[];
  loginMode?: boolean;
  referer?: string;
  videoQuality?: string;
  requireYoutube?: boolean;
  requirePornhub?: boolean;
}

export interface ChromeProfilesBulkActionRequest {
  action: 'open' | 'restart' | 'close';
  profileNames: string[];
  url?: string;
  minDelaySeconds?: number;
  maxDelaySeconds?: number;
  referer?: string;
  videoQuality?: string;
  requireYoutube?: boolean;
  requirePornhub?: boolean;
}

export interface ChromeProfileActionResult {
  name: string;
  status: string;
  message: string;
}

export interface ChromeProfilesUrlCheckRequest {
  url: string;
}

export interface ChromeProfilesUrlCheckStatus {
  url: string;
  checking?: boolean;
  okCount: number;
  completedCount?: number;
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

export interface DesktopUpdateStatus {
  currentVersion: string;
  latestVersion: string;
  updateAvailable: boolean;
  releaseUrl: string;
  downloadUrl: string;
  error?: string;
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

export interface QueuePostingJob {
  id: string;
  accountId: string;
  accountLabel: string;
  platform: 'x' | 'threads';
  running: boolean;
  intervalHours: number;
  postsPerRun: number;
  minimumReady: number;
  randomizeUpToHour: boolean;
  startedAt: string | null;
  lastHeartbeatAt: string | null;
  nextRunAt: string | null;
  nextRunOffsetMinutes: number | null;
  lastRunMessage: string;
}

export interface QueuePostingJobRequest {
  accountId: string;
  platform: 'x' | 'threads';
  intervalHours: number;
  postsPerRun: number;
  minimumReady: number;
  randomizeUpToHour: boolean;
}
