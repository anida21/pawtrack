import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';

/** A 401 from the gateway means the session is gone (e.g. refresh failed): start a new login. */
export const unauthorizedInterceptor: HttpInterceptorFn = (req, next) => {
  const apiBaseUrl = inject(API_BASE_URL);
  const oidc = inject(OidcSecurityService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && req.url.startsWith(apiBaseUrl)) {
        oidc.authorize();
      }
      return throwError(() => error);
    }),
  );
};
