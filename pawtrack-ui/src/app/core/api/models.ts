// Friendly aliases for the types generated from the OpenAPI contracts.
import type { components as IdentityApi } from './generated/identity';
import type { components as PetApi } from './generated/pet';

type IdentitySchemas = IdentityApi['schemas'];
type PetSchemas = PetApi['schemas'];

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

/** Roles that may create, edit and delete pets and upload photos (SITTER is read-only). */
export const WRITE_ROLES: readonly HouseholdRole[] = ['OWNER', 'MEMBER'];

export const canWrite = (role: HouseholdRole | undefined): boolean =>
  role !== undefined && WRITE_ROLES.includes(role);
