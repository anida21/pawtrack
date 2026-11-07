import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-base-url';
import {
  HealthSummary,
  Medication,
  MedicationRequest,
  Vaccination,
  VaccinationRequest,
  VetVisit,
  VetVisitRequest,
  Weight,
  WeightRequest,
} from './models';

/**
 * Health records of one pet (routed by the gateway to the health service).
 * Access follows the pet: whoever may read the pet may also write its health records (health.yaml).
 */
@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/api/v1/pets`;

  private url(petId: string, path: string, id?: string): string {
    return `${this.base}/${enc(petId)}/${path}${id ? `/${enc(id)}` : ''}`;
  }

  summary(petId: string): Observable<HealthSummary> {
    return this.http.get<HealthSummary>(this.url(petId, 'summary'));
  }

  // Vaccinations – newest first. Create/update publish health.vaccination.recorded (reminders).
  vaccinations(petId: string): Observable<Vaccination[]> {
    return this.http.get<Vaccination[]>(this.url(petId, 'vaccinations'));
  }
  createVaccination(petId: string, request: VaccinationRequest): Observable<Vaccination> {
    return this.http.post<Vaccination>(this.url(petId, 'vaccinations'), request);
  }
  updateVaccination(petId: string, id: string, request: VaccinationRequest): Observable<Vaccination> {
    return this.http.put<Vaccination>(this.url(petId, 'vaccinations', id), request);
  }
  deleteVaccination(petId: string, id: string): Observable<void> {
    return this.http.delete<void>(this.url(petId, 'vaccinations', id));
  }

  // Vet visits – newest first, may be in the future (planned).
  vetVisits(petId: string): Observable<VetVisit[]> {
    return this.http.get<VetVisit[]>(this.url(petId, 'vet-visits'));
  }
  createVetVisit(petId: string, request: VetVisitRequest): Observable<VetVisit> {
    return this.http.post<VetVisit>(this.url(petId, 'vet-visits'), request);
  }
  updateVetVisit(petId: string, id: string, request: VetVisitRequest): Observable<VetVisit> {
    return this.http.put<VetVisit>(this.url(petId, 'vet-visits', id), request);
  }
  deleteVetVisit(petId: string, id: string): Observable<void> {
    return this.http.delete<void>(this.url(petId, 'vet-visits', id));
  }

  // Medications – newest first.
  medications(petId: string, activeOnly = false): Observable<Medication[]> {
    const params = activeOnly ? new HttpParams().set('activeOnly', 'true') : undefined;
    return this.http.get<Medication[]>(this.url(petId, 'medications'), { params });
  }
  createMedication(petId: string, request: MedicationRequest): Observable<Medication> {
    return this.http.post<Medication>(this.url(petId, 'medications'), request);
  }
  updateMedication(petId: string, id: string, request: MedicationRequest): Observable<Medication> {
    return this.http.put<Medication>(this.url(petId, 'medications', id), request);
  }
  deleteMedication(petId: string, id: string): Observable<void> {
    return this.http.delete<void>(this.url(petId, 'medications', id));
  }

  // Weights – oldest first (chart order). No update/delete in the contract.
  weights(petId: string, range: { from?: string; to?: string } = {}): Observable<Weight[]> {
    let params = new HttpParams();
    if (range.from) params = params.set('from', range.from);
    if (range.to) params = params.set('to', range.to);
    return this.http.get<Weight[]>(this.url(petId, 'weights'), { params });
  }
  createWeight(petId: string, request: WeightRequest): Observable<Weight> {
    return this.http.post<Weight>(this.url(petId, 'weights'), request);
  }
}

const enc = encodeURIComponent;
