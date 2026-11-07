import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { API, HOME_ID, makePet, signIn, testProviders } from '../../../testing/fixtures';
import { SessionStore } from '../../core/session.store';
import { PetListPage } from './pet-list.page';

describe('PetListPage', () => {
  let fixture: ComponentFixture<PetListPage>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [PetListPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), ...testProviders()],
    });
    signIn(TestBed.inject(SessionStore));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PetListPage);
  });

  const listRequest = () => http.expectOne((r) => r.url === `${API}/api/v1/pets`);

  it('turns query parameters into the list request and renders the pets', async () => {
    fixture.componentRef.setInput('householdId', HOME_ID);
    fixture.componentRef.setInput('species', 'DOG');
    fixture.componentRef.setInput('page', '1');
    fixture.componentRef.setInput('size', '24');
    await fixture.whenStable();

    const req = listRequest();
    expect(req.request.params.get('householdId')).toBe(HOME_ID);
    expect(req.request.params.get('species')).toBe('DOG');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('24');
    req.flush({ content: [makePet(), makePet({ id: 'p2', name: 'Milo', species: 'CAT' })], page: 1, size: 24, totalElements: 26, totalPages: 2 });
    await fixture.whenStable();

    const names = [...fixture.nativeElement.querySelectorAll('.pet-card .name')].map((el: Element) => el.textContent?.trim());
    expect(names).toEqual(['Luna', 'Milo']);
  });

  it('ignores invalid query values', async () => {
    fixture.componentRef.setInput('species', 'DRAGON');
    fixture.componentRef.setInput('page', '-3');
    fixture.componentRef.setInput('size', '1000');
    await fixture.whenStable();
    const req = listRequest();
    expect(req.request.params.has('species')).toBe(false);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('12');
    req.flush({ content: [], totalElements: 0 });
  });

  it('shows the empty state with an add button for a writer', async () => {
    await fixture.whenStable();
    listRequest().flush({ content: [], totalElements: 0 });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No pets yet');
    expect(fixture.nativeElement.querySelector('a[href="/pets/new"]')).not.toBeNull();
  });

  it('shows a ProblemDetail error', async () => {
    await fixture.whenStable();
    listRequest().flush(
      { title: 'Service Unavailable', status: 503, detail: 'Identity is not reachable' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('app-error-state')?.textContent).toContain('Identity is not reachable');
  });

  it('resets to the first page when a filter changes', async () => {
    await fixture.whenStable();
    listRequest().flush({ content: [], totalElements: 0 });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (fixture.componentInstance as unknown as { setFilter(c: object): void }).setFilter({ species: 'CAT' });
    expect(navigate).toHaveBeenCalledWith([], {
      queryParams: { species: 'CAT', page: null },
      queryParamsHandling: 'merge',
    });
  });
});
