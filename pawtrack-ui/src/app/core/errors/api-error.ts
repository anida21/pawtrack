import { HttpErrorResponse } from '@angular/common/http';

/** RFC 7807 body as returned by every PawTrack service. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

/** A normalized API error the UI can render. `status` 0 means the request never reached a server. */
export interface ApiError {
  status: number;
  title: string;
  message: string;
  problem?: ProblemDetail;
}

/** Status-specific, user-facing texts. A context may override them (e.g. 410 on an invitation). */
export type ErrorMessages = Partial<Record<number, string>>;

const DEFAULT_MESSAGES: Record<number, string> = {
  0: 'Cannot reach the PawTrack server. Check your connection and try again.',
  400: 'Some of the data is not valid.',
  401: 'Your session has expired. Please sign in again.',
  403: "You don't have permission to do that.",
  404: 'This item does not exist (anymore).',
  409: 'This action conflicts with the current state.',
  410: 'This link has expired or was already used.',
  413: 'The file is too large.',
  415: 'This file type is not supported.',
  503: 'A PawTrack service is temporarily unavailable. Please try again shortly.',
};

const DEFAULT_TITLES: Record<number, string> = {
  0: 'Offline',
  403: 'No access',
  404: 'Not found',
  409: 'Conflict',
  410: 'Expired',
};

const isProblem = (body: unknown): body is ProblemDetail =>
  typeof body === 'object' && body !== null && ('title' in body || 'detail' in body || 'status' in body);

export function toApiError(error: unknown, messages: ErrorMessages = {}): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: -1, title: 'Unexpected error', message: messages[-1] ?? 'Something went wrong.' };
  }
  const status = error.status;
  const problem = isProblem(error.error) ? error.error : undefined;
  // Context-specific text wins; otherwise prefer the server's detail (it explains *why*), then a generic text.
  const message =
    messages[status] ??
    problem?.detail ??
    DEFAULT_MESSAGES[status] ??
    (status >= 500 ? 'The server had a problem. Please try again later.' : 'The request failed.');
  const title = DEFAULT_TITLES[status] ?? problem?.title ?? (status >= 500 ? 'Server error' : 'Request failed');
  return { status, title, message, problem };
}
