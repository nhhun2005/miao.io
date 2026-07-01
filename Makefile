.PHONY: frontend-install frontend-dev frontend-build frontend-lint backend-test backend-dev compose-up compose-down check-ids

frontend-install:
	cd frontend && npm install

frontend-dev:
	cd frontend && npm run dev

frontend-build:
	cd frontend && npm run build

frontend-lint:
	cd frontend && npm run lint

backend-test:
	docker run --rm -v "$(PWD):/app" -v mimope-maven-cache:/root/.m2 -w /app/backend maven:3.9-eclipse-temurin-17 mvn test

backend-dev:
	docker run --rm -it -p 8080:8080 -v "$(PWD):/app" -v mimope-maven-cache:/root/.m2 -w /app/backend maven:3.9-eclipse-temurin-17 mvn spring-boot:run

compose-up:
	docker compose up --build

compose-down:
	docker compose down

check-ids:
	bash scripts/check-id-consistency.sh
