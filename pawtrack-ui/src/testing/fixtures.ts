import { Provider } from '@angular/core';
import { vi } from 'vitest';
import { Household, HouseholdMembership, Pet, User } from '../app/core/api/models';
import { API_BASE_URL } from '../app/core/api/api-base-url';
import { NotifyService } from '../app/core/notify.service';
import { SessionStore } from '../app/core/session.store';

export const API = 'http://gateway.test';
export const ALICE_ID = '00000000-0000-0000-0000-00000000a11c';
export const BOB_ID = '00000000-0000-0000-0000-000000000b0b';
export const HOME_ID = '11111111-1111-1111-1111-111111111111';
export const CABIN_ID = '22222222-2222-2222-2222-222222222222';

export const alice: User = {
  id: ALICE_ID,
  email: 'alice@pawtrack.local',
  displayName: 'Alice Test',
  timezone: 'Europe/Sarajevo',
  emailNotifications: true,
  createdAt: '2026-01-01T10:00:00Z',
};

export const memberships: HouseholdMembership[] = [
  { householdId: HOME_ID, householdName: 'Home', role: 'OWNER' },
  { householdId: CABIN_ID, householdName: 'Cabin', role: 'SITTER' },
];

export function makePet(overrides: Partial<Pet> = {}): Pet {
  return {
    id: '33333333-3333-3333-3333-333333333333',
    householdId: HOME_ID,
    name: 'Luna',
    species: 'DOG',
    breed: 'Beagle',
    ageInMonths: 26,
    hasPhoto: false,
    createdAt: '2026-02-01T10:00:00Z',
    updatedAt: '2026-02-02T10:00:00Z',
    ...overrides,
  };
}

export function makeHousehold(overrides: Partial<Household> = {}): Household {
  return {
    id: HOME_ID,
    name: 'Home',
    createdAt: '2026-01-01T10:00:00Z',
    members: [
      { userId: ALICE_ID, displayName: 'Alice Test', email: alice.email, role: 'OWNER', joinedAt: '2026-01-01T10:00:00Z' },
      { userId: BOB_ID, displayName: 'Bob Test', email: 'bob@pawtrack.local', role: 'MEMBER', joinedAt: '2026-01-05T10:00:00Z' },
    ],
    ...overrides,
  };
}

export type NotifyMock = Record<'success' | 'warn' | 'error', ReturnType<typeof vi.fn>>;

/** Gateway URL + a NotifyService spy (no snack bars in unit tests). */
export function testProviders(notify: NotifyMock = createNotifyMock()): Provider[] {
  return [
    { provide: API_BASE_URL, useValue: API },
    { provide: NotifyService, useValue: notify },
  ];
}

export function createNotifyMock(): NotifyMock {
  return { success: vi.fn(), warn: vi.fn(), error: vi.fn() };
}

/** Puts the session into the "loaded" state the shell normally establishes. */
export function signIn(store: SessionStore, user: User = alice, households = memberships): void {
  store.user.set(user);
  store.households.set(households);
  store.loaded.set(true);
}
