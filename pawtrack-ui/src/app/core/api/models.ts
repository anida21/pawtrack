// Friendly aliases for the types generated from the OpenAPI contracts.
import type { components as IdentityApi } from './generated/identity';
import type { components as PetApi } from './generated/pet';
import type { components as HealthApi } from './generated/health';
import type { components as NotificationApi } from './generated/notification';

type IdentitySchemas = IdentityApi['schemas'];
type PetSchemas = PetApi['schemas'];
type HealthSchemas = HealthApi['schemas'];
type NotificationSchemas = NotificationApi['schemas'];

export type User = IdentitySchemas['User'];
export type UpdateUserRequest = IdentitySchemas['UpdateUserRequest'];
export type Household = IdentitySchemas['Household'];
export type HouseholdRequest = IdentitySchemas['HouseholdRequest'];
export type Member = IdentitySchemas['Member'];
export type HouseholdRole = IdentitySchemas['HouseholdRole'];
export type HouseholdMembership = IdentitySchemas['HouseholdMembership'];
export type InvitationRequest = IdentitySchemas['InvitationRequest'];
export type InviteRole = InvitationRequest['role'];
export type Invitation = IdentitySchemas['Invitation'];

export type Species = PetSchemas['Species'];
export type Sex = PetSchemas['Sex'];
export type PetRequest = PetSchemas['PetRequest'];
export type Pet = PetSchemas['Pet'];
export type PetPage = PetSchemas['PetPage'];

export type VaccinationRequest = HealthSchemas['VaccinationRequest'];
export type Vaccination = HealthSchemas['Vaccination'];
export type VetVisitRequest = HealthSchemas['VetVisitRequest'];
export type VetVisit = HealthSchemas['VetVisit'];
export type MedicationRequest = HealthSchemas['MedicationRequest'];
export type Medication = HealthSchemas['Medication'];
export type MedicationFrequency = MedicationRequest['frequency'];
export type WeightRequest = HealthSchemas['WeightRequest'];
export type Weight = HealthSchemas['Weight'];
export type HealthSummary = HealthSchemas['HealthSummary'];

export type ReminderStatus = NotificationSchemas['ReminderStatus'];
export type ReminderRequest = NotificationSchemas['ReminderRequest'];
export type Reminder = NotificationSchemas['Reminder'];
export type ReminderSource = NonNullable<Reminder['source']>;

/** Roles that may create, edit and delete pets and upload photos (SITTER is read-only). */
export const WRITE_ROLES: readonly HouseholdRole[] = ['OWNER', 'MEMBER'];

export const canWrite = (role: HouseholdRole | undefined): boolean =>
  role !== undefined && WRITE_ROLES.includes(role);
