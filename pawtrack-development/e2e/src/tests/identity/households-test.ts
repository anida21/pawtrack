import { api } from '../../helpers/api';
import { addMember, createHousehold, userId } from '../../helpers/builders';
import type { Household, HouseholdMembership, Invitation, Problem, User } from '../../helpers/types';

describe('identity: profile, households and invitations', () => {
  const created: string[] = [];

  afterAll(async () => {
    for (const id of created) {
      await api('alice').delete(`/api/v1/households/${id}`);
    }
  });

  it('provisions the user from the token and returns the profile', async () => {
    const me = await api('alice').get<User>('/api/v1/me');

    expect(me.status).toBe(200);
    expect(me.body.email).toBe('alice@pawtrack.local');
    expect(me.body.id).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('rejects requests without a token', async () => {
    const res = await api(null).get('/api/v1/me');
    expect(res.status).toBe(401);
  });

  it('creates a household with the caller as OWNER and lists it under /me/households', async () => {
    const household = await createHousehold('alice');
    created.push(household.id);

    expect(household.members).toEqual([expect.objectContaining({ email: 'alice@pawtrack.local', role: 'OWNER' })]);
    const mine = await api('alice').get<HouseholdMembership[]>('/api/v1/me/households');
    expect(mine.body).toContainEqual(expect.objectContaining({ householdId: household.id, role: 'OWNER' }));
  });

  it('lets an invited user join, and the owner remove them again', async () => {
    const household = await createHousehold('alice');
    created.push(household.id);

    await addMember('alice', household.id, 'bob', 'MEMBER');

    const asBob = await api('bob').get<Household>(`/api/v1/households/${household.id}`);
    expect(asBob.status).toBe(200);
    expect(asBob.body.members.map((m) => m.role).sort()).toEqual(['MEMBER', 'OWNER']);

    const removed = await api('alice').delete(`/api/v1/households/${household.id}/members/${await userId('bob')}`);
    expect(removed.status).toBe(204);
    expect((await api('bob').get(`/api/v1/households/${household.id}`)).status).toBe(403);
  });

  it('only accepts an invitation from the invited email, and only once', async () => {
    const household = await createHousehold('alice');
    created.push(household.id);
    const invite = await api('alice').post<Invitation>(`/api/v1/households/${household.id}/invitations`, {
      email: 'bob@pawtrack.local',
      role: 'SITTER',
    });
    expect(invite.status).toBe(201);

    const wrongUser = await api('alice').post<Problem>(`/api/v1/invitations/${invite.body.code}/accept`);
    expect(wrongUser.status).toBe(403);

    expect((await api('bob').post(`/api/v1/invitations/${invite.body.code}/accept`)).status).toBe(200);
    expect((await api('bob').post(`/api/v1/invitations/${invite.body.code}/accept`)).status).toBe(410);
  });

  it('does not let the last OWNER leave', async () => {
    const household = await createHousehold('alice');
    created.push(household.id);

    const leave = await api('alice').delete<Problem>(`/api/v1/households/${household.id}/members/${await userId('alice')}`);

    expect(leave.status).toBe(409);
    expect(leave.headers.get('content-type')).toContain('application/problem+json');
  });
});
