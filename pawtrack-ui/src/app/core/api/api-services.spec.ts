import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API, HOME_ID, alice, makeHousehold, makePet, memberships, testProviders } from '../../../testing/fixtures';
import { HouseholdService } from './household.service';
import { MeService } from './me.service';
import { PetService } from './pet.service';

describe('API services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ...testProviders()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('MeService', () => {
    it('gets and patches the current user', () => {
      const me = TestBed.inject(MeService);
      let name = '';
      me.get().subscribe((u) => (name = u.displayName));
      http.expectOne({ method: 'GET', url: `${API}/api/v1/me` }).flush(alice);
      expect(name).toBe('Alice Test');

      me.update({ displayName: 'Ali' }).subscribe();
      const patch = http.expectOne({ method: 'PATCH', url: `${API}/api/v1/me` });
      expect(patch.request.body).toEqual({ displayName: 'Ali' });
      patch.flush({ ...alice, displayName: 'Ali' });
    });

    it('lists my households', () => {
      let count = 0;
      TestBed.inject(MeService)
        .households()
        .subscribe((list) => (count = list.length));
      http.expectOne(`${API}/api/v1/me/households`).flush(memberships);
      expect(count).toBe(2);
    });
  });

  describe('HouseholdService', () => {
    it('maps every household operation to the contract paths', () => {
      const api = TestBed.inject(HouseholdService);
      api.get(HOME_ID).subscribe();
      api.create('Cabin').subscribe();
      api.rename(HOME_ID, 'Home sweet home').subscribe();
      api.delete(HOME_ID).subscribe();
      api.removeMember(HOME_ID, 'u-1').subscribe();
      api.invite(HOME_ID, { email: 'bob@pawtrack.local', role: 'SITTER' }).subscribe();
      api.acceptInvitation('a b/c').subscribe();

      const base = `${API}/api/v1/households`;
      http.expectOne({ method: 'GET', url: `${base}/${HOME_ID}` }).flush(makeHousehold());
      expect(http.expectOne({ method: 'POST', url: base }).request.body).toEqual({ name: 'Cabin' });
      expect(http.expectOne({ method: 'PUT', url: `${base}/${HOME_ID}` }).request.body).toEqual({ name: 'Home sweet home' });
      http.expectOne({ method: 'DELETE', url: `${base}/${HOME_ID}` }).flush(null, { status: 204, statusText: 'No Content' });
      http.expectOne({ method: 'DELETE', url: `${base}/${HOME_ID}/members/u-1` }).flush(null);
      expect(http.expectOne({ method: 'POST', url: `${base}/${HOME_ID}/invitations` }).request.body).toEqual({
        email: 'bob@pawtrack.local',
        role: 'SITTER',
      });
      // The code is URL-encoded.
      http.expectOne({ method: 'POST', url: `${API}/api/v1/invitations/a%20b%2Fc/accept` }).flush(makeHousehold());
    });
  });

  describe('PetService', () => {
    it('sends only the filters that are set', () => {
      TestBed.inject(PetService).list({ householdId: HOME_ID, species: undefined, page: 2, size: 12 }).subscribe();
      const req = http.expectOne((r) => r.url === `${API}/api/v1/pets`);
      expect(req.request.params.keys().sort()).toEqual(['householdId', 'page', 'size']);
      expect(req.request.params.get('page')).toBe('2');
      req.flush({ content: [], page: 2, size: 12, totalElements: 0, totalPages: 0 });
    });

    it('creates, updates and deletes pets', () => {
      const api = TestBed.inject(PetService);
      const pet = makePet();
      const body = { householdId: HOME_ID, name: 'Luna', species: 'DOG' as const };
      api.create(body).subscribe();
      api.update(pet.id, body).subscribe();
      api.delete(pet.id).subscribe();
      expect(http.expectOne({ method: 'POST', url: `${API}/api/v1/pets` }).request.body).toEqual(body);
      http.expectOne({ method: 'PUT', url: `${API}/api/v1/pets/${pet.id}` }).flush(pet);
      http.expectOne({ method: 'DELETE', url: `${API}/api/v1/pets/${pet.id}` }).flush(null);
    });

    it('uploads the photo as multipart part "file"', () => {
      const file = new File(['x'], 'luna.png', { type: 'image/png' });
      TestBed.inject(PetService).uploadPhoto('p1', file).subscribe();
      const req = http.expectOne({ method: 'PUT', url: `${API}/api/v1/pets/p1/photo` });
      expect(req.request.body).toBeInstanceOf(FormData);
      expect((req.request.body as FormData).get('file')).toBeInstanceOf(File);
      req.flush(makePet({ hasPhoto: true }));
    });

    it('downloads the photo as a blob', () => {
      TestBed.inject(PetService).photo('p1').subscribe();
      const req = http.expectOne(`${API}/api/v1/pets/p1/photo`);
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['img']));
    });
  });
});
