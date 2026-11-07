import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API, CABIN_ID, HOME_ID, alice, memberships, testProviders } from '../../testing/fixtures';
import { SessionStore } from './session.store';

describe('SessionStore', () => {
  let store: SessionStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ...testProviders()],
    });
    store = TestBed.inject(SessionStore);
    http = TestBed.inject(HttpTestingController);
  });

  it('loads the user and households (sorted by name)', () => {
    store.load().subscribe();
    http.expectOne(`${API}/api/v1/me`).flush(alice);
    http.expectOne(`${API}/api/v1/me/households`).flush(memberships);

    expect(store.loaded()).toBe(true);
    expect(store.user()?.email).toBe(alice.email);
    expect(store.households().map((h) => h.householdName)).toEqual(['Cabin', 'Home']);
  });

  it('knows roles and which households allow writing', () => {
    store.households.set(memberships);
    expect(store.roleIn(HOME_ID)).toBe('OWNER');
    expect(store.roleIn(CABIN_ID)).toBe('SITTER');
    expect(store.roleIn('unknown')).toBeUndefined();
    expect(store.writableHouseholds().map((h) => h.householdId)).toEqual([HOME_ID]);
    expect(store.householdName(CABIN_ID)).toBe('Cabin');
  });
});
