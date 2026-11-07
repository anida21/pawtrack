import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { API, HOME_ID, makeHousehold, memberships, signIn, testProviders } from '../../../testing/fixtures';
import { SessionStore } from '../../core/session.store';
import { AcceptInvitePage } from './accept-invite.page';

describe('AcceptInvitePage', () => {
  let fixture: ComponentFixture<AcceptInvitePage>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [AcceptInvitePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), ...testProviders()],
    });
    signIn(TestBed.inject(SessionStore));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AcceptInvitePage);
    fixture.componentRef.setInput('code', 'abc123');
    await fixture.whenStable();
  });

  const clickAccept = () => {
    const button = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.includes('Accept'),
    ) as HTMLButtonElement;
    button.click();
  };

  it('does nothing until the user accepts', () => {
    http.expectNone(`${API}/api/v1/invitations/abc123/accept`);
  });

  it('joins, refreshes households and opens the household', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    clickAccept();
    http.expectOne({ method: 'POST', url: `${API}/api/v1/invitations/abc123/accept` }).flush(makeHousehold());
    http.expectOne(`${API}/api/v1/me/households`).flush(memberships);
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/households', HOME_ID]);
  });

  it.each([
    [410, 'expired or was already used'],
    [403, 'different email address'],
    [404, 'not valid'],
  ])('explains a %s response', async (status, message) => {
    clickAccept();
    http
      .expectOne(`${API}/api/v1/invitations/abc123/accept`)
      .flush({ status, title: 'x', detail: 'server text' }, { status, statusText: 'x' });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('app-error-state')?.textContent).toContain(message);
  });
});
