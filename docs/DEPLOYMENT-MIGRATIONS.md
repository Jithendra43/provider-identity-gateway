# Database migrations — Flyway, CI, and local Compose

## Canonical source (production + CI image)

All **versioned** and **repeatable** SQL that Flyway applies in **production** lives in:

`services/tefca-gateway-app/src/main/resources/db/migration/`

Spring Boot loads them via `spring.flyway.locations: classpath:db/migration` (see `application-prod.yml`). The **fat-jar Docker build** (`services/tefca-gateway-app/Dockerfile`) copies `services/` into the image and runs `mvn package`, so **every migration ships inside the container** — no separate SQL step in `deploy.yml` is required for RDS.

Compose-only SQL lives separately in:

`services/tefca-gateway-app/src/main/resources/db/compose-migration/`

## GitHub Actions

| Workflow | Role |
|----------|------|
| **`deploy.yml`** | After checkout, runs `scripts/verify-flyway-migrations.sh` so a broken tree cannot build/push an image without migrations. |
| **`ci.yml`** | Same verification before `mvn clean verify`. |

Terraform workflows (`infra-plan.yml`, `infra-apply.yml`) **do not** run migrations; RDS is created empty and the **application applies Flyway** on startup.

## Local Docker Compose

Postgres uses **`database/00_init.sh`**, which applies SQL in this order:
1. prod-equivalent `db/migration` `V*.sql` then `R*.sql`
2. compose-only `db/compose-migration` `V*.sql` then `R*.sql`

The **`migrations`** volume points at the same directory as production Flyway:

`services/tefca-gateway-app/src/main/resources/db/migration/`

and an additional compose-only volume points at:

`services/tefca-gateway-app/src/main/resources/db/compose-migration/`

So local schema init stays aligned with prod first, then applies compose-specific endpoint rewrites.

Compose does **not** mount `database/seed/` anymore: repeatables are sourced from `db/migration/` and `db/compose-migration/` only.

**Changing migrations:**
- production-safe changes: `services/tefca-gateway-app/src/main/resources/db/migration/`
- compose-only changes: `services/tefca-gateway-app/src/main/resources/db/compose-migration/`

**Existing Postgres volume:** if you switch migration sets, remove the old volume so init runs again:

`docker compose down -v` (destructive to local DB data) or `docker volume rm <project>_pgdata`.

## Legacy folder `database/migrations/`

Older copies under `database/migrations/` are **not** used by Compose anymore (Compose mounts the gateway `db/migration` path). See `database/migrations/README.md`. Remove or archive those files in a future cleanup if nothing else references them.
