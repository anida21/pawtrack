import { InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

/** Gateway origin, e.g. `http://localhost:8080`. Overridable in tests. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => environment.apiBaseUrl,
});
