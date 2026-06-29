import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Subject, combineLatest, map, startWith, switchMap } from 'rxjs';
import { ActionResult, QueuePost, QueuePostUpsertRequest } from '../models/dashboard.models';
import { DashboardService } from './dashboard.service';

@Injectable({
  providedIn: 'root'
})
export class AdminUiStateService {
  private readonly dashboardService = inject(DashboardService);
  private readonly refreshTrigger$ = new BehaviorSubject<void>(undefined);
  private readonly queuePlatformSubject = new BehaviorSubject<'x' | 'threads'>('x');
  private readonly actionResultSubject = new Subject<ActionResult>();

  readonly actionResult$ = this.actionResultSubject.asObservable();
  readonly queuePlatform$ = this.queuePlatformSubject.asObservable();

  readonly vm$ = combineLatest([this.refreshTrigger$, this.queuePlatformSubject]).pipe(
    switchMap(([, platform]) => combineLatest([
      this.dashboardService.getSummary(),
      this.dashboardService.getQueue(platform)
    ])),
    map(([summary, queue]) => ({ summary, queue, queuePlatform: this.queuePlatformSubject.value })),
    startWith({
      summary: {
        queueReady: 0,
        threadsReady: 0,
        xReady: 0,
        postingStatus: 'stopped' as const,
        lastDailyMessage: 'Loading dashboard...',
        lastThreadsMessage: 'Loading dashboard...',
        publisherAccounts: {
          activeAccountId: 'loading',
          activeAccountLabel: 'Loading...',
          xAccountLabel: 'Loading...',
          xModeLabel: 'Loading...',
          threadsAccountLabel: 'Loading...',
          availableAccounts: []
        },
        jobStatus: null
      },
      queue: [],
      queuePlatform: 'x' as const
    })
  );

  refresh(): void {
    this.refreshTrigger$.next();
  }

  pushActionResult(result: ActionResult): void {
    this.actionResultSubject.next(this.humanizeActionResult(result));
    this.refresh();
  }

  setQueuePlatform(platform: 'x' | 'threads'): void {
    this.queuePlatformSubject.next(platform);
  }

  xReadyPosts(queue: QueuePost[]): QueuePost[] {
    return queue.filter((post) =>
      post.status === 'ready'
      && (post.platforms ?? []).includes('x')
      && !post.published?.['x']
    );
  }

  editableQueue(queue: QueuePost[]): QueuePost[] {
    return queue
      .filter((post) => post.status !== 'posted')
      .sort((left, right) => left.createdAt < right.createdAt ? 1 : -1);
  }

  parsePlatforms(value: string): string[] {
    return value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  }

  sanitizeQueueUpsertRequest(request: QueuePostUpsertRequest): QueuePostUpsertRequest {
    return this.sanitizeStringFields(request);
  }

  sanitizeStringFields<T extends object>(value: T): T {
    const sanitizedEntries = Object.entries(value as { [key: string]: unknown }).map(([key, entryValue]) => {
      if (typeof entryValue === 'string') {
        return [key, this.stripBom(entryValue)];
      }
      return [key, entryValue];
    });
    return Object.fromEntries(sanitizedEntries) as T;
  }

  stripBom(value: string | null | undefined): string {
    if (!value) {
      return '';
    }
    return value.replace(/^\uFEFF+/, '');
  }

  private humanizeActionResult(result: ActionResult): ActionResult {
    if (result.success) {
      return {
        ...result,
        command: this.humanizeCommand(result.command),
        message: this.humanizeSuccessMessage(result.message)
      };
    }

    return {
      ...result,
      command: this.humanizeCommand(result.command),
      message: this.humanizeErrorMessage(result.message)
    };
  }

  private humanizeCommand(command: string): string {
    const labels: Record<string, string> = {
      'attach-open-images': 'Attach photos',
      'auto-create': 'Generate posts',
      'clean-duplicate-images': 'Queue photos',
      'daily': 'Daily run',
      'delete-queue-post': 'Queue',
      'fill-missing-photos': 'Queue photos',
      'move-queue-post': 'Queue',
      'open-x-composer': 'X composer',
      'open-x-login-browser': 'X login',
      'publish-queued-threads': 'Publish Threads',
      'publish-thread': 'Publish Threads',
      'publish-x': 'Publish X',
      'publish-x-via-selenium': 'Send X',
      'switch-account': 'Publishing account'
    };

    return labels[command] ?? command;
  }

  private humanizeSuccessMessage(message: string): string {
    if (message.includes('Post generation started in the background')) {
      return 'Post generation has started. Refresh the dashboard in about a minute to see the new queue count.';
    }

    if (message.includes('Active publishing account changed')) {
      return 'Publishing account changed.';
    }

    return this.compactMessage(message);
  }

  private humanizeErrorMessage(message: string): string {
    const compact = this.compactMessage(message);
    const lower = compact.toLowerCase();

    if (lower.includes('threads_access_token')) {
      return 'Threads is not connected yet. Finish Threads login, then add the new access token to the server settings.';
    }

    if (lower.includes('threads_user_id')) {
      return 'Threads is missing the profile ID. Add THREADS_USER_ID together with the access token.';
    }

    if (lower.includes('api access blocked')) {
      return 'Meta blocked this Threads app/token. Check the Meta app access, permissions, and the connected Threads profile.';
    }

    if (lower.includes('request failed (0') || lower.includes('network')) {
      return 'The admin panel could not reach the server. Refresh the page and try again.';
    }

    if (lower.includes('403') || lower.includes('forbidden')) {
      return 'The server refused this action. Refresh the page; if it repeats, the server settings need a quick check.';
    }

    return compact || 'Something went wrong. Try again in a moment.';
  }

  private compactMessage(message: string): string {
    return (message ?? '')
      .replace(/\{[^}]*\}/g, '')
      .replace(/https?:\/\/\S+/g, '')
      .replace(/[A-Z]:\\[^ ]+/g, '')
      .replace(/\s+/g, ' ')
      .trim();
  }
}
