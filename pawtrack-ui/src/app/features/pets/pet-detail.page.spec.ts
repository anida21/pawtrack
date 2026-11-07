import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API, CABIN_ID, NotifyMock, createNotifyMock, makePet, signIn, testProviders } from '../../../testing/fixtures';
import { SessionStore } from '../../core/session.store';
import { PetDetailPage, photoProblem } from './pet-detail.page';

describe('photoProblem', () => {
  it('accepts jpeg/png/webp up to 5 MB', () => {
    expect(photoProblem(new File(['x'], 'a.webp', { type: 'image/webp' }))).toBeNull();
    expect(photoProblem(new File(['x'], 'a.gif', { type: 'image/gif' }))).toContain('JPEG, PNG or WebP');
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.jpg', { type: 'image/jpeg' });
    expect(photoProblem(big)).toContain('5 MB');
  });
});

describe('PetDetailPage', () => {
  let fixture: ComponentFixture<PetDetailPage>;
  let http: HttpTestingController;
  let notify: NotifyMock;

  beforeEach(async () => {
    notify = createNotifyMock();
    TestBed.configureTestingModule({
      imports: [PetDetailPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), ...testProviders(notify)],
    });
    signIn(TestBed.inject(SessionStore));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PetDetailPage);
    fixture.componentRef.setInput('petId', 'p1');
    await fixture.whenStable();
  });

  const text = () => fixture.nativeElement.textContent as string;

  it('shows the pet with edit actions for owners', async () => {
    http.expectOne(`${API}/api/v1/pets/p1`).flush(makePet({ id: 'p1', microchipNumber: '123456789012345' }));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Luna');
    expect(text()).toContain('123456789012345');
    expect(text()).toContain('2 yrs 2 mos');
    expect(text()).toContain('Edit');
    expect(text()).toContain('Add photo');
  });

  it('hides edit actions for sitters', async () => {
    http.expectOne(`${API}/api/v1/pets/p1`).flush(makePet({ id: 'p1', householdId: CABIN_ID }));
    await fixture.whenStable();
    expect(text()).not.toContain('Edit');
    expect(fixture.nativeElement.querySelector('input[type=file]')).toBeNull();
  });

  it.each([
    [404, 'does not exist or was deleted'],
    [403, 'not a member of'],
  ])('renders %s as an error state', async (status, message) => {
    http.expectOne(`${API}/api/v1/pets/p1`).flush({ status, title: 'x' }, { status, statusText: 'x' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('app-error-state')?.textContent).toContain(message);
  });

  it('uploads a photo and maps 415 to a friendly message', async () => {
    http.expectOne(`${API}/api/v1/pets/p1`).flush(makePet({ id: 'p1' }));
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('input[type=file]') as HTMLInputElement;
    const file = new File(['x'], 'luna.png', { type: 'image/png' });
    Object.defineProperty(input, 'files', { value: [file] });
    input.dispatchEvent(new Event('change'));

    http.expectOne({ method: 'PUT', url: `${API}/api/v1/pets/p1/photo` }).flush(
      { status: 415, title: 'Unsupported Media Type' },
      { status: 415, statusText: 'Unsupported Media Type' },
    );
    expect(notify.error).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ 415: expect.any(String) }));
  });

  it('rejects unsupported files before uploading', async () => {
    http.expectOne(`${API}/api/v1/pets/p1`).flush(makePet({ id: 'p1' }));
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('input[type=file]') as HTMLInputElement;
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'a.gif', { type: 'image/gif' })] });
    input.dispatchEvent(new Event('change'));
    http.expectNone(`${API}/api/v1/pets/p1/photo`);
    expect(notify.warn).toHaveBeenCalled();
  });
});
