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
  private readonly actionResultSubject = new Subject<ActionResult>();

  readonly actionResult$ = this.actionResultSubject.asObservable();

  readonly vm$ = this.refreshTrigger$.pipe(
    switchMap(() => combineLatest([
      this.dashboardService.getSummary(),
      this.dashboardService.getQueue()
    ])),
    map(([summary, queue]) => ({ summary, queue })),
    startWith({
      summary: {
        queueReady: 0,
        threadsReady: 0,
        xReady: 0,
        postingStatus: 'stopped' as const,
        lastDailyMessage: 'Loading dashboard...',
        lastThreadsMessage: 'Loading dashboard...',
        publisherAccounts: {
          xAccountLabel: 'Loading...',
          xModeLabel: 'Loading...',
          threadsAccountLabel: 'Loading...'
        },
        jobStatus: null
      },
      queue: []
    })
  );

  refresh(): void {
    this.refreshTrigger$.next();
  }

  pushActionResult(result: ActionResult): void {
    this.actionResultSubject.next(result);
    this.refresh();
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
}
