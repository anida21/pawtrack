import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { toApiError } from './api-error';

const problem = (status: number, body: unknown) =>
  new HttpErrorResponse({
    status,
    error: body,
    headers: new HttpHeaders({ 'Content-Type': 'application/problem+json' }),
  });

describe('toApiError', () => {
  it('uses the ProblemDetail detail when there is no context message', () => {
    const error = toApiError(problem(409, { title: 'Conflict', status: 409, detail: 'The last owner cannot leave' }));
    expect(error).toMatchObject({ status: 409, title: 'Conflict', message: 'The last owner cannot leave' });
    expect(error.problem?.detail).toBe('The last owner cannot leave');
  });

  it('prefers a context-specific message for the status', () => {
    const error = toApiError(problem(410, { title: 'Gone', detail: 'Invitation expired' }), { 410: 'Ask for a new one.' });
    expect(error.message).toBe('Ask for a new one.');
    expect(error.title).toBe('Expired');
  });

  it('falls back to a generic text when the body is not a problem', () => {
    expect(toApiError(problem(403, 'nope')).message).toBe("You don't have permission to do that.");
    expect(toApiError(problem(404, null)).title).toBe('Not found');
  });

  it('treats status 0 as offline', () => {
    const error = toApiError(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    expect(error.title).toBe('Offline');
    expect(error.message).toContain('Cannot reach');
  });

  it('describes unknown 5xx errors as server errors', () => {
    expect(toApiError(problem(502, null))).toMatchObject({ title: 'Server error' });
  });

  it('handles non-HTTP errors', () => {
    expect(toApiError(new Error('boom'))).toMatchObject({ status: -1, title: 'Unexpected error' });
  });
});
