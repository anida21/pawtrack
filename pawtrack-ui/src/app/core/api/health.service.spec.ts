import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API, testProviders } from '../../../testing/fixtures';
import { HealthService } from './health.service';

describe('HealthService', () => {
  let http: HttpTestingController;
  let api: HealthService;
  const pets = `${API}/api/v1/pets/p1`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ...testProviders()],
    });
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(HealthService);
  });

  afterEach(() => http.verify());

  it('maps record operations to the contract paths', () => {
    const vaccination = { vaccine: 'Rabies', administeredOn: '2026-01-01' };
    api.summary('p1').subscribe();
    api.vaccinations('p1').subscribe();
    api.createVaccination('p1', vaccination).subscribe();
    api.updateVaccination('p1', 'v1', vaccination).subscribe();
    api.deleteVaccination('p1', 'v1').subscribe();
    api.vetVisits('p1').subscribe();
    api.deleteVetVisit('p1', 'vv1').subscribe();
    api.medications('p1').subscribe();
    api.updateMedication('p1', 'm1', { name: 'X', dosage: '1', startDate: '2026-01-01', frequency: 'DAILY' }).subscribe();
    api.createWeight('p1', { measuredOn: '2026-01-01', weightKg: 4.2 }).subscribe();

    http.expectOne({ method: 'GET', url: `${pets}/summary` }).flush({});
    http.expectOne({ method: 'GET', url: `${pets}/vaccinations` }).flush([]);
    expect(http.expectOne({ method: 'POST', url: `${pets}/vaccinations` }).request.body).toEqual(vaccination);
    http.expectOne({ method: 'PUT', url: `${pets}/vaccinations/v1` }).flush({});
    http.expectOne({ method: 'DELETE', url: `${pets}/vaccinations/v1` }).flush(null);
    http.expectOne({ method: 'GET', url: `${pets}/vet-visits` }).flush([]);
    http.expectOne({ method: 'DELETE', url: `${pets}/vet-visits/vv1` }).flush(null);
    const meds = http.expectOne((r) => r.method === 'GET' && r.url === `${pets}/medications`);
    expect(meds.request.params.has('activeOnly')).toBe(false);
    meds.flush([]);
    http.expectOne({ method: 'PUT', url: `${pets}/medications/m1` }).flush({});
    http.expectOne({ method: 'POST', url: `${pets}/weights` }).flush({});
  });

  it('passes activeOnly and the weight range as query parameters', () => {
    api.medications('p1', true).subscribe();
    api.weights('p1', { from: '2026-01-01' }).subscribe();
    expect(http.expectOne((r) => r.url === `${pets}/medications`).request.params.get('activeOnly')).toBe('true');
    const weights = http.expectOne((r) => r.url === `${pets}/weights`);
    expect(weights.request.params.get('from')).toBe('2026-01-01');
    expect(weights.request.params.has('to')).toBe(false);
  });
});
