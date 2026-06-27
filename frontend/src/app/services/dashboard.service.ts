import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
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

  getQueue(): Observable<QueuePost[]> {
    return this.http.get<QueuePost[]>(`${API_BASE_URL}/queue`);
  }

  createQueuePost(request: QueuePostUpsertRequest): Observable<QueuePost> {
    return this.http.post<QueuePost>(`${API_BASE_URL}/queue`, request);
  }

  updateQueuePost(id: string, request: QueuePostUpsertRequest): Observable<QueuePost> {
    return this.http.put<QueuePost>(`${API_BASE_URL}/queue/${id}`, request);
  }

  markPostPublished(id: string, platform: string): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/queue/${id}/mark-published/${platform}`, {});
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

  runDaily(): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/actions/daily`, {});
  }

  runAutoCreate(): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/actions/auto-create`, {});
  }

  attachOpenImages(): Observable<ActionResult> {
    return this.http.post<ActionResult>(`${API_BASE_URL}/actions/attach-open-images`, {});
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
