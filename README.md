# Behind The Smile Monorepo

This repository is now organized as a transitional monorepo for the social posting system.

## Structure

```text
backend/        Spring Boot backend rewrite
frontend/       Angular admin UI scaffold
legacy-node/    Existing Node.js implementation kept as fallback
scripts/        PowerShell automation and local helper scripts
generated/      Runtime logs, queue files, HTML outputs
```

## Current State

- `backend/` contains the new Spring Boot implementation for queue generation, daily runs, Threads publishing, X publishing, and X composer HTML generation.
- `frontend/` contains the Angular admin shell for queue, status, logs, and operator actions.
- `legacy-node/` still contains the original Node.js implementation and is still used by the existing automation scripts until Java is validated locally.

## Config Layout

The runtime configuration now lives under `backend/config/`:

- `backend/config/.env`
- `backend/config/.env.example`
- `backend/config/content-plan.json`

Shared project files that still remain at repo root:

- `CONTENT_STRATEGY.md`
- `generated/`

## Local Commands

Legacy Node flow:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-daily.ps1 1 1 8
powershell -ExecutionPolicy Bypass -File .\scripts\run-auto-create.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\publish-threads-local.ps1 1
```

Spring Boot flow:

```powershell
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.arguments=daily --threads-per-run 1 --x-per-run 1 --minimum-ready 8"
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.arguments=auto-create"
```

Both flows read the same config from `backend/config/`.

Human-readable account labels can also be set there:

- `backend/config/.env`
- `X_ACCOUNT_LABEL=@your_x_account`
- `THREADS_ACCOUNT_LABEL=@your_threads_account`

Angular flow:

```powershell
cd frontend
npm install
npx ng serve
```

The Angular dev server proxies `/api` requests to `http://localhost:8080`, so start the Spring Boot backend first.

## Important Notes

- This environment still does not have working `java`, `mvn`, or `npm`, so the new backend/frontend could not be executed here.
- The migration is intentionally incremental. Do not remove `legacy-node/` until the Spring Boot and Angular paths are verified locally.
- Existing automation scripts now explicitly target `legacy-node/`.
- `legacy-node/` keeps fallback support for root `.env`, but the canonical config location is now `backend/config/`.
