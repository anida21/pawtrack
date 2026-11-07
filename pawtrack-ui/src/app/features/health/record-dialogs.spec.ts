import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { vi } from 'vitest';
import { API, testProviders } from '../../../testing/fixtures';
import { MedicationDialog } from './medication-dialog';
import { VaccinationDialog } from './vaccination-dialog';
import { VetVisitDialog } from './vet-visit-dialog';
import { WeightDialog } from './weight-dialog';

const base = `${API}/api/v1/pets/p1`;

interface FormHost {
  form: { patchValue(v: object): void };
}

async function open<T>(component: Type<T>, record?: object) {
  const close = vi.fn();
  TestBed.configureTestingModule({
    imports: [component],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      ...testProviders(),
      { provide: MAT_DIALOG_DATA, useValue: { petId: 'p1', record } },
      { provide: MatDialogRef, useValue: { close } },
    ],
  });
  const fixture: ComponentFixture<T> = TestBed.createComponent(component);
  await fixture.whenStable();
  const http = TestBed.inject(HttpTestingController);
  const submit = async (patch: object) => {
    (fixture.componentInstance as unknown as FormHost).form.patchValue(patch);
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };
  return { fixture, http, close, submit };
}

describe('health record dialogs', () => {
  it('creates a vaccination without empty optional fields', async () => {
    const { http, close, submit } = await open(VaccinationDialog);
    await submit({ vaccine: ' Rabies ', administeredOn: '2026-01-10', nextDueDate: '2027-01-10' });
    const req = http.expectOne({ method: 'POST', url: `${base}/vaccinations` });
    expect(req.request.body).toEqual({ vaccine: 'Rabies', administeredOn: '2026-01-10', nextDueDate: '2027-01-10' });
    req.flush({ id: 'v1' });
    expect(close).toHaveBeenCalledWith({ id: 'v1' });
  });

  it('blocks a next due date before the vaccination date', async () => {
    const { fixture, http, submit } = await open(VaccinationDialog);
    await submit({ vaccine: 'Rabies', administeredOn: '2026-01-10', nextDueDate: '2025-01-10' });
    http.expectNone(`${base}/vaccinations`);
    expect(fixture.nativeElement.textContent).toContain('cannot be before');
  });

  it('updates an existing vaccination and shows server errors', async () => {
    const { fixture, http, close, submit } = await open(VaccinationDialog, {
      id: 'v1',
      vaccine: 'Rabies',
      administeredOn: '2026-01-10',
    });
    await submit({ clinic: 'Vet Centar' });
    http
      .expectOne({ method: 'PUT', url: `${base}/vaccinations/v1` })
      .flush({ status: 400, detail: 'administeredOn must not be in the future' }, { status: 400, statusText: 'x' });
    await fixture.whenStable();
    expect(close).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.server-error').textContent).toContain('must not be in the future');
  });

  it('sends vet visit time as ISO instant and omits an empty cost', async () => {
    const { http, submit } = await open(VetVisitDialog);
    await submit({ visitDate: '2026-03-04T09:30', reason: 'Check-up' });
    const body = http.expectOne({ method: 'POST', url: `${base}/vet-visits` }).request.body;
    expect(body.visitDate).toBe(new Date('2026-03-04T09:30').toISOString());
    expect(body).not.toHaveProperty('cost');
    expect(body.currency).toBe('BAM');
  });

  it('creates a medication', async () => {
    const { http, submit } = await open(MedicationDialog);
    await submit({ name: 'Apoquel', dosage: '1 tablet', frequency: 'TWICE_DAILY', startDate: '2026-11-01', timeOfDay: '08:00' });
    expect(http.expectOne({ method: 'POST', url: `${base}/medications` }).request.body).toEqual({
      name: 'Apoquel',
      dosage: '1 tablet',
      frequency: 'TWICE_DAILY',
      startDate: '2026-11-01',
      timeOfDay: '08:00',
    });
  });

  it('validates the weight range', async () => {
    const { http, submit } = await open(WeightDialog);
    await submit({ weightKg: 0 });
    http.expectNone(`${base}/weights`);
    await submit({ weightKg: 12.345, measuredOn: '2026-11-01' });
    expect(http.expectOne({ method: 'POST', url: `${base}/weights` }).request.body).toEqual({
      weightKg: 12.345,
      measuredOn: '2026-11-01',
    });
  });
});
