import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-base-url';
import { Pet, PetPage, PetRequest, Species } from './models';

export interface PetFilter {
  householdId?: string;
  species?: Species;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class PetService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_BASE_URL)}/api/v1/pets`;

  list(filter: PetFilter = {}): Observable<PetPage> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filter)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<PetPage>(this.url, { params });
  }

  get(petId: string): Observable<Pet> {
    return this.http.get<Pet>(`${this.url}/${enc(petId)}`);
  }

  create(request: PetRequest): Observable<Pet> {
    return this.http.post<Pet>(this.url, request);
  }

  /** Full replace: optional fields that are omitted are cleared. */
  update(petId: string, request: PetRequest): Observable<Pet> {
    return this.http.put<Pet>(`${this.url}/${enc(petId)}`, request);
  }

  delete(petId: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${enc(petId)}`);
  }

  uploadPhoto(petId: string, file: File): Observable<Pet> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.put<Pet>(`${this.url}/${enc(petId)}/photo`, body);
  }

  /**
   * The endpoint answers 302 to a short-lived pre-signed URL. An <img> cannot send the bearer
   * token, so the image is fetched with HttpClient (the browser follows the redirect).
   */
  photo(petId: string): Observable<Blob> {
    return this.http.get(`${this.url}/${enc(petId)}/photo`, { responseType: 'blob' });
  }
}

const enc = encodeURIComponent;
