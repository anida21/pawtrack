# PawTrack

Pet-care platform: pet profiles, health records, reminders and shared households.
Built as microservices with Spring Boot 4 (Java 17), Angular, Keycloak, PostgreSQL, RabbitMQ and MinIO.

| Folder | What |
|---|---|
| [pawtrack-development](pawtrack-development) | Local environment (docker-compose), API contracts, E2E tests, architecture docs |
| [pawtrack-identity](pawtrack-identity) | Users, households, invitations |
| pawtrack-pet | Pets and photos *(planned)* |
| pawtrack-health | Vaccinations, vet visits, medications, weight *(planned)* |
| pawtrack-notification | Reminders and email *(planned)* |
| pawtrack-gateway | API gateway *(planned)* |
| pawtrack-ui | Angular frontend *(planned)* |

Each service builds, tests and deploys on its own: own build file, Dockerfile and CI workflow
(`.github/workflows/<service>.yml`, triggered only by changes in that folder).

- Architecture: [pawtrack-development/docs/ARCHITECTURE.md](pawtrack-development/docs/ARCHITECTURE.md)
- Contracts: [pawtrack-development/contracts](pawtrack-development/contracts)
- Run locally: [pawtrack-development/README.md](pawtrack-development/README.md)
