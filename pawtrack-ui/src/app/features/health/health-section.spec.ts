import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { API, NotifyMock, createNotifyMock, testProviders } from '../../../testing/fixtures';
import { HealthSection } from './health-section';

describe('HealthSection', () => {
  let fixture: ComponentFixture<HealthSection>;
  let http: HttpTestingController;
  let notify: NotifyMock;
  const dialog = { open: vi.fn() };
  const base = `${API}/api/v1/pets/p1`;

  beforeEach(async () => {
    notify = createNotifyMock();
    dialog.open.mockReset().mockReturnValue({ afterClosed: () => of(true) });
    TestBed.configureTestingModule({
      imports: [HealthSection],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ...testProviders(notify),
        { provide: MatDialog, useValue: dialog },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HealthSection);
    fixture.componentRef.setInput('petId', 'p1');
    fixture.componentRef.setInput('petName', 'Luna');
    await fixture.whenStable();
  });

  function flushAll(overrides: { summary?: object; vaccinations?: object[] } = {}) {
    http.expectOne(`${base}/summary`).flush(
      overrides.summary ?? { petId: 'p1', latestWeightKg: 12.4, overdueVaccinations: 1, nextVaccinationDue: '2027-01-01', activeMedications: 2 },
    );
    http.expectOne(`${base}/vaccinations`).flush(
      overrides.vaccinations ?? [{ id: 'v1', vaccine: 'Rabies', administeredOn: '2024-01-01', nextDueDate: '2025-01-01' }],
    );
    http.expectOne(`${base}/vet-visits`).flush([]);
    http.expectOne((r) => r.url === `${base}/medications`).flush([]);
    http.expectOne((r) => r.url === `${base}/weights`).flush([]);
  }

  const text = () => fixture.nativeElement.textContent as string;

  it('loads summary and all record lists for the pet', async () => {
    flushAll();
    await fixture.whenStable();
    expect(text()).toContain('12.4 kg');
    expect(text()).toContain('1 overdue');
    expect(text()).toContain('Rabies');
    expect(text()).toContain('Overdue since');
  });

  it('asks for the last year of weights by default', async () => {
    http.expectOne(`${base}/summary`).flush({});
    http.expectOne(`${base}/vaccinations`).flush([]);
    http.expectOne(`${base}/vet-visits`).flush([]);
    http.expectOne((r) => r.url === `${base}/medications`).flush([]);
    const weights = http.expectOne((r) => r.url === `${base}/weights`);
    expect(weights.request.params.get('from')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    weights.flush([]);
  });

  it('shows a load error inline with retry', async () => {
    http.expectOne(`${base}/summary`).flush({ status: 503, detail: 'Pet service is unavailable' }, { status: 503, statusText: 'x' });
    http.expectOne(`${base}/vaccinations`).flush({ status: 503 }, { status: 503, statusText: 'x' });
    http.expectOne(`${base}/vet-visits`).flush([]);
    http.expectOne((r) => r.url === `${base}/medications`).flush([]);
    http.expectOne((r) => r.url === `${base}/weights`).flush([]);
    await fixture.whenStable();
    expect(text()).toContain('Pet service is unavailable');
  });

  it('deletes a vaccination after confirmation and reloads list and summary', async () => {
    flushAll();
    await fixture.whenStable();
    const del = fixture.nativeElement.querySelector('[aria-label="Delete Rabies"]') as HTMLButtonElement;
    del.click();
    http.expectOne({ method: 'DELETE', url: `${base}/vaccinations/v1` }).flush(null);
    http.expectOne(`${base}/vaccinations`).flush([]);
    http.expectOne(`${base}/summary`).flush({});
    expect(notify.success).toHaveBeenCalledWith('Deleted');
  });

  it('opens the dialog to add a record and refreshes after saving', async () => {
    flushAll();
    await fixture.whenStable();
    const add = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.includes('Add'),
    ) as HTMLButtonElement;
    add.click();
    expect(dialog.open).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ data: { petId: 'p1', record: undefined } }));
    http.expectOne(`${base}/vaccinations`).flush([]);
    http.expectOne(`${base}/summary`).flush({});
    expect(notify.success).toHaveBeenCalledWith('Vaccination saved');
  });
});
