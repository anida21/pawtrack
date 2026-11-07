import { Routes } from '@angular/router';
import { autoLoginPartialRoutesGuard } from 'angular-auth-oidc-client';
import { Shell } from './layout/shell';

export const routes: Routes = [
  {
    path: '',
    component: Shell,
    // Unauthenticated visitors are sent to Keycloak and come back to the URL they asked for
    // (this is what makes /invite/:code links work for users who are not signed in yet).
    canActivate: [autoLoginPartialRoutesGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'pets' },
      {
        path: 'pets',
        title: 'Pets · PawTrack',
        loadComponent: () => import('./features/pets/pet-list.page').then((m) => m.PetListPage),
      },
      {
        path: 'pets/new',
        title: 'New pet · PawTrack',
        loadComponent: () => import('./features/pets/pet-form.page').then((m) => m.PetFormPage),
      },
      {
        path: 'pets/:petId',
        title: 'Pet · PawTrack',
        loadComponent: () => import('./features/pets/pet-detail.page').then((m) => m.PetDetailPage),
      },
      {
        path: 'pets/:petId/edit',
        title: 'Edit pet · PawTrack',
        loadComponent: () => import('./features/pets/pet-form.page').then((m) => m.PetFormPage),
      },
      {
        path: 'households',
        title: 'Households · PawTrack',
        loadComponent: () => import('./features/households/household-list.page').then((m) => m.HouseholdListPage),
      },
      {
        path: 'households/:householdId',
        title: 'Household · PawTrack',
        loadComponent: () =>
          import('./features/households/household-detail.page').then((m) => m.HouseholdDetailPage),
      },
      {
        path: 'reminders',
        title: 'Reminders · PawTrack',
        loadComponent: () => import('./features/reminders/reminders.page').then((m) => m.RemindersPage),
      },
      {
        path: 'invite/:code',
        title: 'Invitation · PawTrack',
        loadComponent: () => import('./features/invite/accept-invite.page').then((m) => m.AcceptInvitePage),
      },
      {
        path: 'profile',
        title: 'Profile · PawTrack',
        loadComponent: () => import('./features/profile/profile.page').then((m) => m.ProfilePage),
      },
      {
        path: '**',
        title: 'Not found · PawTrack',
        loadComponent: () => import('./features/not-found.page').then((m) => m.NotFoundPage),
      },
    ],
  },
];
