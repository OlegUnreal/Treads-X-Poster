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

### Автоматичний локальний запуск (рекомендований)

Щоб одним командом підняти і бекенд, і фронтенд:

```powershell
# Повний локальний запуск (без Docker): збирає та стартує обидва процеси.
powershell -ExecutionPolicy Bypass -File .\scripts\start-auto-stack.ps1

# Те саме в Docker (Spring Boot + Frontend + PostgreSQL контейнер):
powershell -ExecutionPolicy Bypass -File .\scripts\start-auto-stack.ps1 -UseDocker
```

Зупинка:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-auto-stack.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\stop-auto-stack.ps1 -UseDocker
```

Що всередині:

- `docker-compose.local.yml` — піднімає `postgres`, `backend`, `frontend`.
- `scripts/start-auto-stack.ps1` — автоматично перевіряє залежності, збирає backend/front,
  зберігає PIDs і чекає на `/api/health`.
- `scripts/stop-auto-stack.ps1` — чисто зупиняє процеси/контейнери.

Наразі бекенд працює в DB-first режимі: стан акаунтів/черг/чернеток/автоjobs зберігається в PostgreSQL (або H2 локально, якщо налаштування БД не задані),
а `queue.jsonl`/`posts.jsonl` можуть залишатися лише як legacy джерело для разової міграції.

Human-readable account labels can also be set there:

- `backend/config/.env`
- `X_ACCOUNT_LABEL=@your_x_account`
- `THREADS_ACCOUNT_LABEL=@your_threads_account`

Multiple publishing profiles can be configured in the same `.env`. The UI can switch the active profile, and the selected profile is stored in the database (`APP_SETTINGS` table, key `active_account_id`), with legacy compatibility fallback to `ACTIVE_ACCOUNT_FILE` during migration.

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

### Windows Playback App

Use this when you want the Playback UI as a local Windows application instead of the hosted admin page. It builds the Spring Boot backend, builds the Angular UI, starts the backend on `127.0.0.1:8081`, serves the UI locally, and opens the Playback screen in Electron.

Install or update the project on another Windows computer:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-or-update-local.ps1
```

From a fresh machine, you can run the same script from a downloaded repository checkout. It clones or updates the `windows` branch into:

```text
C:\Users\<you>\Documents\BTS-Twitter-Treads
```

If you already have a `profiles.env`, copy it during setup:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-or-update-local.ps1 -ProfilesEnvSource "D:\backup\profiles.env"
```

Or download it from the server endpoint:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-or-update-local.ps1 -ProfilesEnvUrl "http://167.233.93.6/api/actions/chrome-profiles/profiles-env"
```

The script leaves Chrome runtime profiles under `C:\Users\<you>\chrome-proxy-profiles\` untouched.

Update an existing local checkout:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update-local.ps1
```

Rebuild the packaged `.exe` during update:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update-local.ps1 -BuildExe
```

Build the `.exe`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows-app.ps1
```

Packaging requires a full JDK with `jlink.exe`, not only a JRE. The script tries to find it automatically from `JAVA_HOME`, `PATH`, and common `Program Files` locations. If it cannot find one, install JDK 21 and reopen PowerShell, or set `JAVA_HOME` manually:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.x.x"
```

Run the generated app:

```powershell
.\frontend\release\BehindTheSmilePlayback.exe
```

Release builds are produced by the `Windows release` GitHub Actions workflow from the `windows` branch. The downloadable artifact/release asset is named with a `win-` prefix, for example:

```text
win-BehindTheSmilePlayback-123.exe
```

Development launch without packaging:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-windows-app.ps1
```

After the first successful build, a faster local launch can reuse the existing build:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-windows-app.ps1 -SkipBuild
```

The app uses the same WebShare/Doppler profile sync as the local scripts and stores Chrome profile runtime files under:

```text
C:\Users\<you>\chrome-proxy-profiles\
```

### Local Proxy Chrome Profiles

Use this when you want to run the same isolated Chrome proxy profiles on the Windows workstation instead of the remote VNC server.

Runtime files live outside the repository:

```text
C:\Users\ZEPHYRUS\chrome-proxy-profiles\
```

That folder must contain `profiles.env`. It includes proxy credentials, so keep it out of git. The local launcher also copies `remote-chrome-profiles/proxy-forwarder.py` there automatically.

For many WebShare proxies, use `WEBSHARE_API_TOKEN` in Doppler so production deploy can refresh the current proxy list automatically. Manual fallback: keep the provider export outside git as `webshare-proxies.txt` in the same runtime folder and generate `profiles.env` from it:

```powershell
python .\remote-chrome-profiles\import-webshare-proxies.py "$env:USERPROFILE\chrome-proxy-profiles\webshare-proxies.txt" --env "$env:USERPROFILE\chrome-proxy-profiles\profiles.env"
```

The local launcher refreshes WebShare proxies automatically before starting profiles. Daily use only needs count and URL:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-profiles.ps1 3 profile-home
```

The WebShare refresh keeps stable profile assignments: if a proxy still exists in the fresh provider list, it stays attached to the same `ipN` profile. Each `ipN` also keeps its own Chrome data folder, so cookies and login state survive app restarts, redeploys, and normal proxy refreshes.

Optional per-profile browser settings can also live in `profiles.env`. Use global values as defaults and override only profiles that need a different browser shape:

```env
WINDOW_SIZE="1000,760"
LANGUAGE="en-US"
ACCEPT_LANGUAGE="en-US,en;q=0.9"
TIMEZONE="Europe/London"
USER_AGENT=""
BROWSER_PATH=""
AUTO_BROWSER_PROFILE="true"

WINDOW_SIZE_ip1="1366,768"
LANGUAGE_ip1="en-GB"
ACCEPT_LANGUAGE_ip1="en-GB,en;q=0.9"
TIMEZONE_ip1="Europe/London"
```

When profiles are generated from the WebShare API, `AUTO_BROWSER_PROFILE="true"` makes the importer set `LANGUAGE_ipN`, `ACCEPT_LANGUAGE_ipN`, `TIMEZONE_ipN`, and `WINDOW_SIZE_ipN` automatically from WebShare's `country_code`. For countries with multiple time zones, `TIMEZONE_ipN` uses WebShare's `city_name` when it matches a known city, then falls back to the country default. The importer also writes `PROXY_COUNTRY_ipN` and `PROXY_CITY_ipN` when WebShare provides them. Set `AUTO_BROWSER_PROFILE="false"` only if you want to manage all per-profile browser values manually.

Keep these values consistent with the proxy country. Avoid random or conflicting combinations; a small set of realistic browser profiles is safer than aggressive fingerprint spoofing.

Use `BROWSER_PATH` when you want to test another Chromium-based browser without changing the app code:

```env
BROWSER_PATH="C:\Program Files\Google\Chrome\Application\chrome.exe"
BROWSER_PATH_ip5="C:\Path\To\Chromium.exe"
```

`Check URL` runs proxy checks in parallel. Tune it with:

```env
CHECK_CONCURRENCY=5
CHECK_TIMEOUT_SECONDS=15
```

The Windows app automatically downloads the server `profiles.env` on startup from:

```text
http://167.233.93.6/api/actions/chrome-profiles/profiles-env
```

It saves the file to `C:\Users\<you>\chrome-proxy-profiles\profiles.env` before the local backend starts. If the server is temporarily unavailable, the app keeps using the last local copy. Override the URL with `BTS_PROFILES_ENV_SYNC_URL` when the server address changes. If `PROFILES_ENV_DOWNLOAD_TOKEN` is set on the server, set the same value locally as `BTS_PROFILES_ENV_SYNC_TOKEN`.

The sync is two-way. While the Windows app is open, edits to the local `profiles.env` are uploaded back to the same server endpoint with `PUT` after a short debounce. Use `PROFILES_ENV_SYNC_TOKEN` on the server for both download and upload, or set `PROFILES_ENV_DOWNLOAD_TOKEN` / `PROFILES_ENV_UPLOAD_TOKEN` separately. The Windows app sends `BTS_PROFILES_ENV_SYNC_TOKEN`.

Use the full `start-local-chrome-profiles.ps1 -SkipWebShareSync` command only when you intentionally want to reuse the existing local `profiles.env`.

Start one or more local profiles:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-chrome-profiles.ps1 -Count 2 -Url "https://www.youtube.com/watch?v=WU7Wo8IJoh4"
```

Pornhub or another video page:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-chrome-profiles.ps1 -Count 2 -Url "https://www.pornhub.com/view_video.php?viewkey=VIDEO_KEY"
```

Open profile marker pages instead of a video URL:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-chrome-profiles.ps1 -Count 2 -Url profile-home
```

Run specific profiles:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-chrome-profiles.ps1 -Profiles ip2,ip4 -Url "https://example.com/video"
```

Stop local Chrome profiles and local proxy forwarders:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-local-chrome-profiles.ps1
```

Notes:

- Each profile uses its own Chrome user data directory under `C:\Users\ZEPHYRUS\chrome-proxy-profiles\data\`.
- If `PROXY_ipN` points to `127.0.0.1`, the script starts a local Python proxy forwarder for the authenticated upstream proxy.
- Keep `INCOGNITO_MODE=false` and leave `INCOGNITO_DOMAINS` empty for YouTube or any site where a profile must remember sign-in state, cookies, or age confirmation.
- Treat each `ipN` as one long-lived device/profile. Open `profile-home` or the target site's homepage first, complete any manual sign-in once, then launch videos with only count and URL.
- Webshare/free proxy traffic can hit bandwidth limits; if a proxy returns `Bandwidth limit reached`, the launcher is working but the provider limit is exhausted.

## Production Setup

The backend can run on a server as long as runtime files live in a persistent folder. Configure that folder with `DATA_DIR` instead of relying on files inside the repository checkout.

By default account settings, queues, generated drafts and posting jobs are stored in the database through Spring Data JPA. Keep `SPRING_DATASOURCE_*` set for PostgreSQL in production; if unset, the app falls back to local H2 storage.

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

## CI/CD (GitHub Actions)

The repository has a production deploy workflow in `.github/workflows/deploy-production.yml` that runs on push to `master` and `main`:

- `git push` to `master` (including merge commit from PR) starts automatic deployment.
- manual deployment is also available with `workflow_dispatch`.

On each run the workflow checks out the target branch, runs `deploy-production.sh`, builds backend/frontend and restarts the service. State persistence is provided by:

- PostgreSQL (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`) in production,
- local H2 file DB fallback when datasource is not set.

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
- `DOPPLER_TOKEN` - optional Doppler service token for production config sync.

Alternatively, use `DEPLOY_SSH_KEY` instead of `DEPLOY_PASSWORD` if you want key-based SSH later.

Optional repository variable:

- `DEPLOY_PATH` - server checkout path, defaults to `/opt/behind-the-smile`.

The workflow deploys automatically on pushes to `main` and `master`. It can also be started manually from the Actions tab and pointed at another branch.

### Doppler production config

Doppler can be the source of truth for production environment values. The deploy script still supports the old server `.env` flow, but if `DOPPLER_TOKEN` is present it downloads the latest Doppler config into:

```text
/opt/behind-the-smile/backend/config/.env
```

Then systemd starts Spring Boot with that file as `EnvironmentFile`.

Recommended Doppler setup:

```text
Project: behind-the-smile
Environment: production
Config: prd
```

Add the same keys that exist in `backend/config/.env.production.example`, for example:

```text
OPENAI_API_KEY
OPENAI_MODEL
DATA_DIR
CONTENT_PLAN_FILE
X_PUBLISH_MODE
X_BROWSER_PROFILE_DIR
THREADS_ACCESS_TOKEN
THREADS_USER_ID
```

For production deploys, generate a Doppler Service Token for the production config and store it in GitHub as the repository secret `DOPPLER_TOKEN`. On the next deploy, `scripts/deploy-production.sh` installs the Doppler CLI if needed and runs:

```bash
doppler secrets download --no-file --format env
```

The downloaded values replace the server `.env` before the backend service is restarted. If `DOPPLER_TOKEN` is not set, deploy keeps using the existing server-side `.env`.

## Important Notes

- The migration is intentionally incremental. Do not remove `legacy-node/` until the Spring Boot and Angular paths are verified locally.
- Existing automation scripts now explicitly target `legacy-node/`.
- `legacy-node/` keeps fallback support for root `.env`, but the canonical config location is now `backend/config/`.
- Do not commit real `.env` files, access tokens, refresh tokens, Selenium profiles, cookies, or generated runtime data.
