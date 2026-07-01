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

- Node.js 20 or newer
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

Run the backend through Docker Maven:

```sh
make backend-dev
```

Run the full stack with Docker Compose:

```sh
docker compose up --build
```

## Services

- Frontend: http://localhost:5173
- Backend: http://localhost:8080
- Backend health: http://localhost:8080/actuator/health