// Response/request types from the contracts (generated into src/proxy before every run).
import type { components as identity } from '../proxy/identity';
import type { components as pet } from '../proxy/pet';
import type { components as health } from '../proxy/health';
import type { components as notification } from '../proxy/notification';

export type User = identity['schemas']['User'];
export type Household = identity['schemas']['Household'];
export type HouseholdMembership = identity['schemas']['HouseholdMembership'];
export type Invitation = identity['schemas']['Invitation'];

export type Pet = pet['schemas']['Pet'];
export type PetRequest = pet['schemas']['PetRequest'];
export type PetPage = pet['schemas']['PetPage'];

export type Vaccination = health['schemas']['Vaccination'];
export type VaccinationRequest = health['schemas']['VaccinationRequest'];
export type Medication = health['schemas']['Medication'];
export type Weight = health['schemas']['Weight'];
export type HealthSummary = health['schemas']['HealthSummary'];

export type Reminder = notification['schemas']['Reminder'];

export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
}
