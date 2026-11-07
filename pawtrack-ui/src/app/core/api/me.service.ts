import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-base-url';
import { HouseholdMembership, UpdateUserRequest, User } from './models';

@Injectable({ providedIn: 'root' })
export class MeService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL)}/api/v1/me`;

  /** Also provisions the user (and a default household) on first call. */
  get(): Observable<User> {
    return this.http.get<User>(this.url);
  }

  update(request: UpdateUserRequest): Observable<User> {
    return this.http.patch<User>(this.url, request);
  }

  households(): Observable<HouseholdMembership[]> {
    return this.http.get<HouseholdMembership[]>(`${this.url}/households`);
  }
}
