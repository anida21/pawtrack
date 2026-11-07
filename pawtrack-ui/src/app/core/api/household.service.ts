import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-base-url';
import { Household, Invitation, InvitationRequest } from './models';

@Injectable({ providedIn: 'root' })
export class HouseholdService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  private readonly url = `${this.base}/api/v1/households`;

  get(householdId: string): Observable<Household> {
    return this.http.get<Household>(`${this.url}/${enc(householdId)}`);
  }

  create(name: string): Observable<Household> {
    return this.http.post<Household>(this.url, { name });
  }

  /** OWNER only. */
  rename(householdId: string, name: string): Observable<Household> {
    return this.http.put<Household>(`${this.url}/${enc(householdId)}`, { name });
  }

  /** OWNER only. */
  delete(householdId: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${enc(householdId)}`);
  }

  /** OWNER removes anyone, a member removes themself. Last OWNER → 409. */
  removeMember(householdId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${enc(householdId)}/members/${enc(userId)}`);
  }

  /** OWNER only. Returns a one-time code valid for 7 days. */
  invite(householdId: string, request: InvitationRequest): Observable<Invitation> {
    return this.http.post<Invitation>(`${this.url}/${enc(householdId)}/invitations`, request);
  }

  /** 403 = invited email differs, 404 = unknown code, 410 = expired or already used. */
  acceptInvitation(code: string): Observable<Household> {
    return this.http.post<Household>(`${this.base}/api/v1/invitations/${enc(code)}/accept`, null);
  }
}

const enc = encodeURIComponent;
