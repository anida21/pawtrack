import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { API, CABIN_ID, HOME_ID, NotifyMock, createNotifyMock, makePet, signIn, testProviders } from '../../../testing/fixtures';
import { SessionStore } from '../../core/session.store';
import { PetFormPage, toRequest } from './pet-form.page';

describe('toRequest', () => {
  it('trims text and omits empty optional fields', () => {
    expect(
      toRequest({
        householdId: HOME_ID,
        name: '  Luna ',
        species: 'DOG',
        breed: ' ',
        sex: '',
        birthDate: '',
        neutered: true,
        microchipNumber: '',
        color: 'brown',
        notes: '',
      }),
    ).toEqual({ householdId: HOME_ID, name: 'Luna', species: 'DOG', neutered: true, color: 'brown' });
  });
});

describe('PetFormPage', () => {
  let fixture: ComponentFixture<PetFormPage>;
  let http: HttpTestingController;
  let notify: NotifyMock;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    notify = createNotifyMock();
    TestBed.configureTestingModule({
      imports: [PetFormPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), ...testProviders(notify)],
    });
    signIn(TestBed.inject(SessionStore));
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(PetFormPage);
  });

  const form = () => (fixture.componentInstance as unknown as { form: PetFormPage['form'] }).form;
  const submit = async () => {
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };

  it('preselects the only writable household and creates the pet', async () => {
    await fixture.whenStable();
    expect(form().controls.householdId.value).toBe(HOME_ID);

    form().patchValue({ name: 'Luna', species: 'DOG' });
    await submit();

    const req = http.expectOne({ method: 'POST', url: `${API}/api/v1/pets` });
    expect(req.request.body).toEqual({ householdId: HOME_ID, name: 'Luna', species: 'DOG', neutered: false });
    req.flush(makePet());
    await fixture.whenStable();
    expect(notify.success).toHaveBeenCalledWith('Luna added');
    expect(navigate).toHaveBeenCalledWith(['/pets', makePet().id]);
  });

  it('does not submit an invalid form', async () => {
    await fixture.whenStable();
    form().patchValue({ name: 'Luna', species: 'DOG', microchipNumber: '123' });
    await submit();
    http.expectNone(`${API}/api/v1/pets`);
    expect(fixture.nativeElement.textContent).toContain('exactly 15 digits');
  });

  it('loads the pet when editing and sends a full replace', async () => {
    fixture.componentRef.setInput('petId', 'p1');
    await fixture.whenStable();
    http.expectOne(`${API}/api/v1/pets/p1`).flush(makePet({ id: 'p1', color: 'tricolour' }));
    await fixture.whenStable();
    expect(form().controls.breed.value).toBe('Beagle');

    form().patchValue({ color: '' });
    await submit();
    const req = http.expectOne({ method: 'PUT', url: `${API}/api/v1/pets/p1` });
    expect(req.request.body).toEqual({ householdId: HOME_ID, name: 'Luna', species: 'DOG', breed: 'Beagle', neutered: false });
    req.flush(makePet({ id: 'p1' }));
  });

  it('refuses to edit a pet of a household where the user is a sitter', async () => {
    fixture.componentRef.setInput('petId', 'p1');
    await fixture.whenStable();
    http.expectOne(`${API}/api/v1/pets/p1`).flush(makePet({ id: 'p1', householdId: CABIN_ID }));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('app-error-state')?.textContent).toContain('Pet-sitters');
  });

  it('shows the server error from a ProblemDetail', async () => {
    await fixture.whenStable();
    form().patchValue({ name: 'Luna', species: 'DOG' });
    await submit();
    http
      .expectOne(`${API}/api/v1/pets`)
      .flush({ title: 'Bad Request', status: 400, detail: 'birthDate must be in the past' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.server-error')?.textContent).toContain('birthDate must be in the past');
  });
});
