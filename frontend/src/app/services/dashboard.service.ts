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

  createPhotoBatch(request: {
    photos: File[];
    prompt: string;
    topic: string;
    tone: string;
    language: string;
    platforms: string[];
    accountIds: string[];
    publishNow: boolean;
  }): Observable<ActionResult> {
    const formData = new FormData();
    request.photos.forEach((photo) => formData.append('photos', photo, photo.name));
    formData.append('prompt', request.prompt);
    formData.append('topic', request.topic);
    formData.append('tone', request.tone);
    formData.append('language', request.language);
    formData.append('platforms', request.platforms.join(','));
    formData.append('accountIds', request.accountIds.join(','));
    formData.append('publishNow', String(request.publishNow));
    return this.http.post<ActionResult>(`${API_BASE_URL}/photo-batch`, formData);
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
