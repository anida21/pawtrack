import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { vi } from 'vitest';
import { API, testProviders } from '../../../testing/fixtures';
import { unauthorizedInterceptor } from './unauthorized.interceptor';

describe('unauthorizedInterceptor', () => {
  const authorize = vi.fn();
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    authorize.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([unauthorizedInterceptor])),
        provideHttpClientTesting(),
        ...testProviders(),
        { provide: OidcSecurityService, useValue: { authorize } },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  it('starts a new login when the gateway answers 401 and still propagates the error', () => {
    let status = 0;
    http.get(`${API}/api/v1/me`).subscribe({ error: (e) => (status = e.status) });
    controller.expectOne(`${API}/api/v1/me`).flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(authorize).toHaveBeenCalledOnce();
    expect(status).toBe(401);
  });

  it('ignores other errors and other hosts', () => {
    http.get(`${API}/api/v1/me`).subscribe({ error: () => undefined });
    http.get('https://elsewhere.test/x').subscribe({ error: () => undefined });
    controller.expectOne(`${API}/api/v1/me`).flush(null, { status: 403, statusText: 'Forbidden' });
    controller.expectOne('https://elsewhere.test/x').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(authorize).not.toHaveBeenCalled();
  });
});
