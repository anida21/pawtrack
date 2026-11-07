import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { authInterceptor } from 'angular-auth-oidc-client';
import { routes } from './app.routes';
import { provideKeycloakAuth } from './core/auth/auth.config';
import { unauthorizedInterceptor } from './core/auth/unauthorized.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ scrollPositionRestoration: 'top' })),
    provideHttpClient(withInterceptors([authInterceptor(), unauthorizedInterceptor])),
    provideKeycloakAuth(),
    provideAppInitializer(() => {
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined');
    }),
  ],
};
