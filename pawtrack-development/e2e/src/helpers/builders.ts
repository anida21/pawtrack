import { faker } from '@faker-js/faker';
import { api } from './api';
import { UserName } from './config';
import type { Household, Invitation, Pet, PetRequest, VaccinationRequest } from './types';

/** ISO date (yyyy-mm-dd) `days` from today (UTC); negative = in the past. */
export function isoDate(days: number): string {
  return new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);
}

export function petRequest(householdId: string, overrides: Partial<PetRequest> = {}): PetRequest {
  return {
    householdId,
    name: faker.animal.petName().slice(0, 60),
    species: 'DOG',
    breed: faker.animal.dog().slice(0, 80),
    sex: faker.helpers.arrayElement(['MALE', 'FEMALE']),
    birthDate: isoDate(-faker.number.int({ min: 60, max: 4000 })),
    ...overrides,
  };
}

export function vaccinationRequest(overrides: Partial<VaccinationRequest> = {}): VaccinationRequest {
  return {
    vaccine: faker.helpers.arrayElement(['Rabies', 'DHPPi', 'Leptospirosis']),
    administeredOn: isoDate(-355),
    nextDueDate: isoDate(10),
    clinic: 'E2E Vet Clinic',
    ...overrides,
  };
}

/** A fresh household owned by `owner`, named so it is easy to spot in the UI after a test run. */
export async function createHousehold(owner: UserName): Promise<Household> {
  const res = await api(owner).post<Household>('/api/v1/households', { name: `E2E ${faker.word.noun()} ${Date.now()}` });
  expect(res.status).toBe(201);
  return res.body;
}

export async function createPet(user: UserName, householdId: string, overrides: Partial<PetRequest> = {}): Promise<Pet> {
  const res = await api(user).post<Pet>('/api/v1/pets', petRequest(householdId, overrides));
  expect(res.status).toBe(201);
  return res.body;
}

/** Owner invites `member` with `role`, `member` accepts. */
export async function addMember(owner: UserName, householdId: string, member: UserName, role: 'MEMBER' | 'SITTER') {
  const email = member === 'alice' ? 'alice@pawtrack.local' : 'bob@pawtrack.local';
  const invite = await api(owner).post<Invitation>(`/api/v1/households/${householdId}/invitations`, { email, role });
  expect(invite.status).toBe(201);
  const accept = await api(member).post<Household>(`/api/v1/invitations/${invite.body.code}/accept`);
  expect(accept.status).toBe(200);
  return invite.body;
}

export async function userId(user: UserName): Promise<string> {
  const me = await api(user).get<{ id: string }>('/api/v1/me');
  expect(me.status).toBe(200);
  return me.body.id;
}
