# pawtrack-pet

Pet service of **PawTrack**: pet profiles that belong to a household, plus a profile photo per pet stored in MinIO.
Access is checked against the caller's household memberships, which come from the identity service.

Part of the PawTrack platform – see [pawtrack-development](../pawtrack-development) for the architecture and local environment.

## Tech
Spring Boot 4 · Java 17 · PostgreSQL · Liquibase · RabbitMQ · MinIO (S3) · Keycloak (OAuth2 resource server) · MapStruct · Testcontainers

## API
Contract: [`pet.yaml`](../pawtrack-development/contracts/pet.yaml)

| Method | Path | |
|---|---|---|
| GET | `/api/v1/pets` | pets of all my households (`householdId`, `species`, `page`, `size`) |
| POST | `/api/v1/pets` | create, publishes `pet.created` |
| GET / PUT / DELETE | `/api/v1/pets/{petId}` | read / full update (publishes `pet.updated`) / soft delete (publishes `pet.deleted`) |
| PUT | `/api/v1/pets/{petId}/photo` | multipart `file`, jpeg/png/webp, max 5 MB |
| GET | `/api/v1/pets/{petId}/photo` | 302 to a pre-signed URL valid 10 minutes |

SITTERs can read, OWNERs and MEMBERs can also write. Consumes `household.deleted` (queue `pet.household-events`)
and soft-deletes the household's pets.

## Run locally
```bash
# infrastructure (from pawtrack-development)
docker compose up -d
# service (identity must be running on :8081 for access checks)
mvn spring-boot:run
```
- Swagger UI: http://localhost:8082/swagger-ui.html
- Health: http://localhost:8082/actuator/health

| Env var | Default |
|---|---|
| `IDENTITY_URL` | `http://localhost:8081` |
| `MINIO_URL` / `MINIO_PUBLIC_URL` | `http://localhost:9000` (public = host used in pre-signed URLs) |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | local docker-compose values |

## Test
```bash
mvn verify   # requires Docker for Testcontainers (Postgres, RabbitMQ, MinIO)
```
