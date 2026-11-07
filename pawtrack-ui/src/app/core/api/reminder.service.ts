import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-base-url';
import { Reminder, ReminderRequest, ReminderStatus } from './models';

export interface ReminderFilter {
  petId?: string;
  status?: ReminderStatus;
  /** Inclusive, ISO date-time. */
  from?: string;
  /** Exclusive, ISO date-time. */
  to?: string;
}

/** Reminders (notification service). Any household member, SITTER included, may read and change them. */
@Injectable({ providedIn: 'root' })
export class ReminderService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL)}/api/v1/reminders`;

  /** Sorted by dueAt ascending. */
  list(filter: ReminderFilter = {}): Observable<Reminder[]> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filter)) {
      if (value) params = params.set(key, value);
    }
    return this.http.get<Reminder[]>(this.url, { params });
  }

  /** Custom reminder (source CUSTOM). */
  create(request: ReminderRequest): Observable<Reminder> {
    return this.http.post<Reminder>(this.url, request);
  }

  /** PENDING|SENT → CANCELLED. A DONE reminder gives 409. */
  cancel(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(id)}`);
  }

  /** PENDING|SENT → DONE. A CANCELLED reminder gives 409. */
  done(id: string): Observable<Reminder> {
    return this.http.post<Reminder>(`${this.url}/${encodeURIComponent(id)}/done`, null);
  }
}
