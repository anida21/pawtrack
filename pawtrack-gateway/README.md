# pawtrack-gateway

API gateway of **PawTrack**: the single entry point for the Angular UI. Validates Keycloak JWTs, handles CORS and
routes requests by path to the identity, pet, health and notification services.

Part of the PawTrack platform – see [pawtrack-development](../pawtrack-development) for the architecture and local environment.

## Tech
Spring Boot 4 · Java 17 · Spring Cloud Gateway (Server Web MVC) · Keycloak (OAuth2 resource server) · springdoc

## Routes
| Path | Service |
|---|---|
| `/api/v1/me/**`, `/api/v1/households/**`, `/api/v1/invitations/**` | identity |
| `/api/v1/pets/{id}/vaccinations\|vet-visits\|medications\|weights\|summary/**` | health |
| `/api/v1/pets/**` | pet |
| `/api/v1/reminders/**` | notification |

## Configuration
| Env var | Default |
|---|---|
| `IDENTITY_URL` | `http://localhost:8081` |
| `PET_URL` | `http://localhost:8082` |
| `HEALTH_URL` | `http://localhost:8083` |
| `NOTIFICATION_URL` | `http://localhost:8084` |
| `KEYCLOAK_ISSUER_URI` | `http://keycloak.pawtrack.local:8180/realms/pawtrack` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` (comma-separated list) |

## Run locally
```bash
# infrastructure + services (from pawtrack-development)
docker compose up -d
# gateway
mvn spring-boot:run
```
- Swagger UI (all services): http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## Test
```bash
mvn verify   # no Docker needed: services are stubbed, Keycloak is replaced by a test key pair
```
