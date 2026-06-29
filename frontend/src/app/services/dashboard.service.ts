import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AccountSelectionResponse,
  ActionResult,
  BrowserXPublishRequest,
  GeneratePromptRequest,
  GeneratePromptResponse,
  PostingJobRequest,
  PostingJobStatus,
  QueuePost,
  QueuePostUpsertRequest,
  RunSummary
} from '../models/dashboard.models';
import { API_BASE_URL } from '../config/api.config';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  constructor(private readonly http: HttpClient) {}

  getSummary(): Observable<RunSummary> {
    return this.http.get<RunSummary>(`${API_BASE_URL}/summary`);
  }

  getAccounts(): Observable<AccountSelectionResponse> {
    return this.http.get<AccountSelectionResponse>(`${API_BASE_URL}/accounts`);
  }

  switchActiveAccount(accountId: string): Observable<AccountSelectionResponse> {
    return this.http.put<AccountSelectionResponse>(`${API_BASE_URL}/accounts/active`, { accountId });
  }

  getQueue(): Observable<QueuePost[]> {
    return this.http.get<QueuePost[]>(`${API_BASE_URL}/queue`);
  }

  createQueuePost(request: QueuePostUpsertRequest): Observable<QueuePost> {
    return this.http.post<QueuePost>(`${API_BASE_URL}/queue`, request);
  }

  updateQueuePost(id: string, request: QueuePostUpsertRequest): Observable<QueuePost> {
    return this.http.put<QueuePost>(`${API_BASE_URL}/queue/${id}`, request);
  }

  deleteQueuePost(id: string): Observable<ActionResult> {
    return this.http.delete<ActionResult>(`${API_BASE_URL}/queue/${id}`);
  }

  moveQueuePost(id: string, direction: 'up' | 'down'): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/queue/${id}/move/${direction}`, {});
  }

  cleanDuplicateQueueImages(): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/queue/clean-duplicate-images`, {});
  }

  fillMissingQueuePhotos(): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/queue/fill-missing-photos`, {});
  }

  generatePrompt(request: GeneratePromptRequest): Observable<GeneratePromptResponse> {
    return this.http.post<GeneratePromptResponse>(`${API_BASE_URL}/generate`, request);
  }

  getJobStatus(): Observable<PostingJobStatus> {
    return this.http.get<PostingJobStatus>(`${API_BASE_URL}/job`);
  }

  startJob(request: PostingJobRequest): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/job/start`, request);
  }

  updateJob(request: PostingJobRequest): Observable<ActionResult> {
    return this.http.put<ActionResult>(`${API_BASE_URL}/job`, request);
  }

  stopJob(): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/job/stop`, {});
  }

  runAutoCreate(): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/actions/auto-create`, {});
  }

  publishThread(): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/actions/publish-thread`, {});
  }

  publishX(): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/actions/publish-x`, {});
  }

  publishXViaBrowser(request: BrowserXPublishRequest): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/actions/publish-x-browser`, request);
  }

  openXLoginBrowser(): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/actions/open-x-login-browser`, {});
  }
}
