import { api, eventually } from '../../helpers/api';
import { addMember, createHousehold, createPet, petRequest, userId } from '../../helpers/builders';
import type { Pet, PetPage, Problem } from '../../helpers/types';

describe('pet: pets of a household, access through identity', () => {
  const created: string[] = [];

  afterAll(async () => {
    for (const id of created) {
      await api('alice').delete(`/api/v1/households/${id}`);
    }
  });

  async function household(): Promise<string> {
    const h = await createHousehold('alice');
    created.push(h.id);
    return h.id;
  }

  it('creates, reads, updates and deletes a pet', async () => {
    const householdId = await household();
    const pet = await createPet('alice', householdId, { name: 'Luna', species: 'DOG' });
    expect(pet.ageInMonths).toBeGreaterThanOrEqual(0);
    expect(pet.hasPhoto).toBe(false);

    const updated = await api('alice').put<Pet>(`/api/v1/pets/${pet.id}`, petRequest(householdId, { name: 'Lunica', species: 'DOG' }));
    expect(updated.status).toBe(200);
    expect(updated.body.name).toBe('Lunica');

    expect((await api('alice').delete(`/api/v1/pets/${pet.id}`)).status).toBe(204);
    expect((await api('alice').get(`/api/v1/pets/${pet.id}`)).status).toBe(404);
  });

  it('lists only the pets of the requested household', async () => {
    const first = await household();
    const second = await household();
    const dog = await createPet('alice', first, { species: 'DOG' });
    await createPet('alice', second, { species: 'CAT' });

    const page = await api('alice').get<PetPage>(`/api/v1/pets?householdId=${first}`);

    expect(page.status).toBe(200);
    expect(page.body.content?.map((p) => p.id)).toEqual([dog.id]);
  });

  it('validates the request (400 problem)', async () => {
    const householdId = await household();
    const res = await api('alice').post<Problem>('/api/v1/pets', { householdId, name: '', species: 'DRAGON' });

    expect(res.status).toBe(400);
    expect(res.headers.get('content-type')).toContain('application/problem+json');
  });

  it('hides pets from users outside the household', async () => {
    const pet = await createPet('alice', await household());

    expect((await api('bob').get(`/api/v1/pets/${pet.id}`)).status).toBe(403);
  });

  it('lets a SITTER read but not change a pet', async () => {
    const householdId = await household();
    const pet = await createPet('alice', householdId);
    await addMember('alice', householdId, 'bob', 'SITTER');

    expect((await api('bob').get(`/api/v1/pets/${pet.id}`)).status).toBe(200);
    expect((await api('bob').put(`/api/v1/pets/${pet.id}`, petRequest(householdId))).status).toBe(403);
    expect((await api('bob').delete(`/api/v1/pets/${pet.id}`)).status).toBe(403);
  });

  it('revokes access of a removed member right away, not after the membership cache expires', async () => {
    const householdId = await household();
    const pet = await createPet('alice', householdId);
    await addMember('alice', householdId, 'bob', 'MEMBER');
    expect((await api('bob').get(`/api/v1/pets/${pet.id}`)).status).toBe(200); // now cached in pet for 60 s

    await api('alice').delete(`/api/v1/households/${householdId}/members/${await userId('bob')}`);

    // household.member.removed evicts the cache entry: well under the 60 s TTL.
    await eventually(async () => ((await api('bob').get(`/api/v1/pets/${pet.id}`)).status === 403 ? true : undefined), 10_000,
      'bob to lose access');
  });

  it('soft-deletes the pets of a deleted household (household.deleted event)', async () => {
    const h = await createHousehold('alice');
    const pet = await createPet('alice', h.id);

    expect((await api('alice').delete(`/api/v1/households/${h.id}`)).status).toBe(204);

    await eventually(async () => ((await api('alice').get(`/api/v1/pets/${pet.id}`)).status === 404 ? true : undefined),
      20_000, 'the pet to be deleted');
  });
});
