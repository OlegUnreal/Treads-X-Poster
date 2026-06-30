#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${APP_DIR:-/opt/behind-the-smile}"
WEB_DIR="${WEB_DIR:-/var/www/behind-the-smile}"
SERVICE_NAME="${SERVICE_NAME:-behind-the-smile}"
BRANCH="${1:-${DEPLOY_BRANCH:-main}}"
BACKEND_PORT="${BACKEND_PORT:-8081}"
FRONTEND_PORT="${FRONTEND_PORT:-4301}"
DISPLAY_VALUE="${DISPLAY_VALUE:-:1}"
XAUTHORITY_VALUE="${XAUTHORITY_VALUE:-/root/.Xauthority}"
PUBLIC_HOST="${PUBLIC_HOST:-_}"

echo "Deploying branch '${BRANCH}' from ${APP_DIR}"

cd "${APP_DIR}"

git fetch origin "${BRANCH}"

if ! git diff --quiet || ! git diff --cached --quiet || [ -n "$(git ls-files --others --exclude-standard)" ]; then
  echo "Server checkout has local changes. Saving them to git stash before deploy."
  git stash push -u -m "auto-stash before production deploy $(date -Iseconds)" || true
fi

if git show-ref --verify --quiet "refs/heads/${BRANCH}"; then
  git checkout "${BRANCH}"
else
  git checkout -b "${BRANCH}" "origin/${BRANCH}"
fi

git reset --hard "origin/${BRANCH}"
git clean -fd -e generated/ -e backend/config/.env

mkdir -p "${APP_DIR}/config"
if [ ! -f "${APP_DIR}/config/content-plan.json" ] && [ -f "${APP_DIR}/backend/config/content-plan.json" ]; then
  cp "${APP_DIR}/backend/config/content-plan.json" "${APP_DIR}/config/content-plan.json"
fi

if [ -d "${APP_DIR}/remote-chrome-profiles" ]; then
  CHROME_PROFILES_DIR="${CHROME_PROFILES_DIR:-/root/chrome-proxy-profiles}"
  mkdir -p "${CHROME_PROFILES_DIR}"
  for file in check-deps.sh proxy-forwarder.py start-all.sh start-profile.sh stop-all.sh profiles.env.example README.md; do
    if [ -f "${APP_DIR}/remote-chrome-profiles/${file}" ]; then
      cp "${APP_DIR}/remote-chrome-profiles/${file}" "${CHROME_PROFILES_DIR}/${file}"
    fi
  done
  chmod +x "${CHROME_PROFILES_DIR}/check-deps.sh" "${CHROME_PROFILES_DIR}/start-all.sh" "${CHROME_PROFILES_DIR}/start-profile.sh" "${CHROME_PROFILES_DIR}/stop-all.sh" || true
fi

mvn -f backend/pom.xml package -DskipTests

JAR_PATH="$(find "${APP_DIR}/backend/target" -maxdepth 1 -type f -name '*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' | head -n 1)"
if [ -z "${JAR_PATH}" ]; then
  echo "Backend jar was not found in backend/target."
  exit 1
fi
ln -sf "${JAR_PATH}" "${APP_DIR}/backend/target/app.jar"

cd "${APP_DIR}/frontend"
if [ -f package-lock.json ]; then
  npm ci || {
    echo "npm ci failed, falling back to npm install without rewriting package-lock.json."
    npm install --no-package-lock
  }
else
  npm install --no-package-lock
fi
npm run build

rm -rf "${WEB_DIR}"
mkdir -p "${WEB_DIR}"

if [ -d "${APP_DIR}/frontend/dist/behind-the-smile-admin/browser" ]; then
  cp -r "${APP_DIR}/frontend/dist/behind-the-smile-admin/browser/." "${WEB_DIR}/"
else
  cp -r "${APP_DIR}/frontend/dist/behind-the-smile-admin/." "${WEB_DIR}/"
fi

cat > "/etc/systemd/system/${SERVICE_NAME}.service" <<EOF
[Unit]
Description=Behind The Smile Posting Backend
After=network.target

[Service]
WorkingDirectory=${APP_DIR}/backend
EnvironmentFile=-${APP_DIR}/backend/config/.env
Environment=SERVER_PORT=${BACKEND_PORT}
Environment=DISPLAY=${DISPLAY_VALUE}
Environment=XAUTHORITY=${XAUTHORITY_VALUE}
ExecStart=/usr/bin/java -jar ${APP_DIR}/backend/target/app.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

cat > /etc/nginx/sites-available/behind-the-smile <<EOF
server {
    listen ${FRONTEND_PORT};
    server_name ${PUBLIC_HOST} _;

    root ${WEB_DIR};
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:${BACKEND_PORT}/api/;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF

for enabled_site in /etc/nginx/sites-enabled/*; do
    [ -e "${enabled_site}" ] || continue
    if [ "$(basename "${enabled_site}")" = "behind-the-smile" ]; then
        continue
    fi
    if grep -Eq "listen[[:space:]]+[^;]*${FRONTEND_PORT}" "${enabled_site}"; then
        echo "Disabling nginx site on port ${FRONTEND_PORT}: ${enabled_site}"
        rm -f "${enabled_site}"
    fi
done

ln -sf /etc/nginx/sites-available/behind-the-smile /etc/nginx/sites-enabled/behind-the-smile

systemctl daemon-reload
systemctl enable "${SERVICE_NAME}"
systemctl restart "${SERVICE_NAME}"

nginx -t
systemctl reload nginx

for attempt in {1..30}; do
  if curl --fail --silent "http://127.0.0.1:${BACKEND_PORT}/api/health" >/dev/null; then
    echo "Backend health check passed."
    echo "Deploy complete."
    exit 0
  fi

  echo "Waiting for backend health check (${attempt}/30)..."
  sleep 2
done

echo "Backend health check failed after 60 seconds."
systemctl status "${SERVICE_NAME}" --no-pager || true
journalctl -u "${SERVICE_NAME}" -n 80 --no-pager || true
exit 1
