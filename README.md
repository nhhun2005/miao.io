# Mimope

Mimope is a browser-based `mope.io`-style multiplayer game foundation using React, PixiJS, TypeScript, and Spring Boot.

## Repository Layout

```text
mimope/
├── assets/      # Migrated source assets from img/, skins/, and icons/
├── backend/     # Spring Boot authoritative game server
├── frontend/    # React + TypeScript + Vite client
├── Makefile     # Common development commands
└── docker-compose.yml
```

## Prerequisites

- Node.js 22 or newer
- npm
- Docker and Docker Compose
- Java 17 or newer if running the backend without Docker
- Maven if running backend commands directly without the provided Docker-based Make targets

## Development

Install frontend dependencies:

```sh
make frontend-install
```

Run the frontend:

```sh
make frontend-dev
```

Run backend tests through Docker Maven:

```sh
make backend-test
```

Run frontend tests and lint:

```sh
make frontend-test
make frontend-lint
```

Run the backend through Docker Maven:

```sh
make backend-dev
```

Run the full stack with Docker Compose:

```sh
docker compose up --build
```

Run a production-style full stack with the frontend nginx WebSocket proxy:

```sh
docker compose -f docker-compose.prod.yml up --build
```

Run the fake-client WebSocket load test against a running backend:

```sh
node scripts/load-test.mjs
```

Verify a from-scratch build and test run (frontend build/test/lint + backend
package/test) against a clean copy of the working tree:

```sh
make verify-clean-clone
# or include a docker compose build:
bash scripts/verify-clean-clone.sh --docker
```

## Services

- Frontend: http://localhost:5173
- Backend: http://localhost:8080
- Backend health: http://localhost:8080/actuator/health

## Release Checks

- Manual multiplayer checklist: `MANUAL_TEST_CHECKLIST.md`
- First playable release notes: `RELEASE_NOTES.md`
