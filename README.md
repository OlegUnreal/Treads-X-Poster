# Behind The Smile Monorepo

This repository is now organized as a transitional monorepo for the social posting system.

## Structure

```text
backend/        Spring Boot backend rewrite
frontend/       Angular admin UI scaffold
legacy-node/    Existing Node.js implementation kept as fallback
scripts/        PowerShell automation and local helper scripts
generated/      Local runtime queue, drafts, Selenium profile, and HTML outputs
```

## Current State

- `backend/` contains the new Spring Boot implementation for queue generation, daily runs, Threads publishing, X publishing, and X composer HTML generation.
- `frontend/` contains the Angular admin shell for queue, status, logs, and operator actions.
- `legacy-node/` still contains the original Node.js implementation and is still used by the existing automation scripts until Java is validated locally.

## Config Layout

The runtime configuration now lives under `backend/config/`:

- `backend/config/.env`
- `backend/config/.env.example`
- `backend/config/.env.production.example`
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

Multiple publishing profiles can be configured in the same `.env`. The UI can switch the active profile, and the selected profile is stored in `DATA_DIR/active-account.txt`.

Example:

```text
SOCIAL_ACCOUNTS=main,second
ACCOUNT_MAIN_LABEL=Main Behind The Smile
ACCOUNT_MAIN_X_ACCOUNT_LABEL=@main_x
ACCOUNT_MAIN_X_PUBLISH_MODE=selenium
ACCOUNT_MAIN_X_BROWSER_PROFILE_DIR=../generated/selenium/main-chrome-profile
ACCOUNT_MAIN_THREADS_ACCOUNT_LABEL=@main_threads
ACCOUNT_MAIN_THREADS_ACCESS_TOKEN=
ACCOUNT_MAIN_THREADS_USER_ID=

ACCOUNT_SECOND_LABEL=Second project
ACCOUNT_SECOND_X_ACCOUNT_LABEL=@second_x
ACCOUNT_SECOND_X_PUBLISH_MODE=selenium
ACCOUNT_SECOND_X_BROWSER_PROFILE_DIR=../generated/selenium/second-chrome-profile
ACCOUNT_SECOND_THREADS_ACCOUNT_LABEL=@second_threads
ACCOUNT_SECOND_THREADS_ACCESS_TOKEN=
ACCOUNT_SECOND_THREADS_USER_ID=
```

Each X account should use its own `X_BROWSER_PROFILE_DIR`, because Selenium publishes from whichever X session is logged into that Chrome profile.

Angular flow:

```powershell
cd frontend
npm install
npx ng serve
```

The Angular dev server proxies `/api` requests to `http://localhost:8080`, so start the Spring Boot backend first.

## Production Setup

The backend can run on a server as long as runtime files live in a persistent folder. Configure that folder with `DATA_DIR` instead of relying on files inside the repository checkout.

Recommended server layout:

```text
/opt/behind-the-smile/app/       Checked-out repository or deployed jar
/opt/behind-the-smile/config/    Private .env and content-plan.json
/opt/behind-the-smile/data/      Queue, drafts, X links, Selenium browser profile
```

Start by copying `backend/config/.env.production.example` to a private `.env` file and filling in the real tokens. Keep that real file out of git.

Important production variables:

- `DATA_DIR` points to persistent runtime storage.
- `CONTENT_PLAN_FILE` points to the content plan JSON on the server.
- `X_BROWSER_PROFILE_DIR` points to a persistent Chrome profile that stays logged in to X.
- `X_PUBLISH_MODE=selenium` uses the browser automation path for X.
- `THREADS_ACCESS_TOKEN` and `THREADS_USER_ID` enable Threads publishing.

For Selenium publishing through a visible Chrome window on a TigerVNC server, start the backend from the same VNC desktop session or export the VNC display before starting it:

```bash
export DISPLAY=:1
java -jar backend/target/social-posting-0.1.0.jar
```

If the backend runs under systemd, add the same display to the service environment:

```ini
Environment=DISPLAY=:1
```

Do not open the configured `X_BROWSER_PROFILE_DIR` manually in another Chrome window while Selenium is publishing, because Chrome locks an active profile.

Build and run the backend:

```powershell
mvn -f backend/pom.xml clean package
java -jar backend/target/social-posting-0.1.0.jar
```

Build the Angular UI:

```powershell
cd frontend
npm install
npm run build
```

Serve `frontend/dist/` with nginx or another static file server, and proxy `/api` to the Spring Boot backend.

Health check:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

The health response includes the active `DATA_DIR`, queue path, draft path, X links path, content plan path, and whether the data directory is writable.

## Deploy

The server deploy script lives at `scripts/deploy-production.sh`. On the server it expects the repository to be checked out at `/opt/behind-the-smile` by default.

Manual deploy on the server:

```bash
/opt/behind-the-smile/scripts/deploy-production.sh main
```

Deploy another branch:

```bash
/opt/behind-the-smile/scripts/deploy-production.sh next-version
```

GitHub Actions can run the same script from `Deploy production`. Add these repository secrets first:

- `DEPLOY_HOST` - server IP or domain.
- `DEPLOY_USER` - SSH user, for example `root`.
- `DEPLOY_PASSWORD` - SSH password for the deploy user.
- `DEPLOY_PORT` - optional, defaults to `22`.

Alternatively, use `DEPLOY_SSH_KEY` instead of `DEPLOY_PASSWORD` if you want key-based SSH later.

Optional repository variable:

- `DEPLOY_PATH` - server checkout path, defaults to `/opt/behind-the-smile`.

The workflow deploys automatically on pushes to `main`. It can also be started manually from the Actions tab and pointed at another branch.

## Important Notes

- The migration is intentionally incremental. Do not remove `legacy-node/` until the Spring Boot and Angular paths are verified locally.
- Existing automation scripts now explicitly target `legacy-node/`.
- `legacy-node/` keeps fallback support for root `.env`, but the canonical config location is now `backend/config/`.
- Do not commit real `.env` files, access tokens, refresh tokens, Selenium profiles, cookies, or generated runtime data.
