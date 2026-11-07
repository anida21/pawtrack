import { EnvironmentProviders } from '@angular/core';
import { LogLevel, provideAuth, withAppInitializerAuthCheck } from 'angular-auth-oidc-client';
import { environment } from '../../../environments/environment';

/** Keycloak login: authorization code flow with PKCE (public client `pawtrack-ui`), refresh tokens. */
export function provideKeycloakAuth(): EnvironmentProviders {
  return provideAuth(
    {
      config: {
        authority: environment.auth.authority,
        clientId: environment.auth.clientId,
        redirectUrl: window.location.origin,
        postLogoutRedirectUri: window.location.origin,
        scope: 'openid profile email',
        responseType: 'code',
        silentRenew: true,
        useRefreshToken: true,
        renewTimeBeforeTokenExpiresInSeconds: 30,
        // The access token is attached only to requests for the gateway.
        secureRoutes: [environment.apiBaseUrl],
        logLevel: environment.production ? LogLevel.Error : LogLevel.Warn,
      },
    },
    withAppInitializerAuthCheck(),
  );
}
