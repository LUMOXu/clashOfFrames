#!/usr/bin/env bash
# Deploy refactored Clash of Frames on 9001 (web) + 9002 (API) without touching old Node on 9000.
set -euo pipefail

OLD_DIR="/opt/clashOfFrames"
NEW_ROOT="/opt/cof-java"
BRANCH="${COF_BRANCH:-feature-dyu}"
SSH_KEY="${SSH_KEY:-/root/.ssh/id_ed25519_github}"
JAR="${NEW_ROOT}/cof-java-boot/cof-boot/target/cof-boot-1.0.0-SNAPSHOT.jar"

log() { echo "[$(date '+%H:%M:%S')] $*"; }

if [ "${COF_SKIP_INSTALL:-0}" = "1" ]; then
  log "=== Skipping apt/clone (COF_SKIP_INSTALL=1) ==="
else

log "=== 1. Install dependencies (nginx, PostgreSQL, Redis, Java 17, Maven) ==="
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq \
  nginx openjdk-17-jdk maven git rsync curl \
  postgresql postgresql-contrib redis-server

log "=== 2. PostgreSQL: user cof / database cof_db ==="
sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='cof'" | grep -q 1 \
  || sudo -u postgres psql -c "CREATE USER cof WITH PASSWORD 'cof';"
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='cof_db'" | grep -q 1 \
  || sudo -u postgres psql -c "CREATE DATABASE cof_db OWNER cof;"
systemctl enable --now postgresql redis-server nginx

log "=== 3. Clone or update repo at ${NEW_ROOT} ==="
export GIT_SSH_COMMAND="ssh -i ${SSH_KEY} -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new"
if [ -d "${NEW_ROOT}/.git" ]; then
  cd "${NEW_ROOT}"
  git fetch --depth 1 origin "$BRANCH"
  git checkout "$BRANCH"
  git reset --hard "origin/${BRANCH}"
else
  rm -rf "${NEW_ROOT}"
  git clone --depth 1 -b "$BRANCH" git@github.com:LUMOXu/clashOfFrames.git "${NEW_ROOT}"
fi

fi

log "=== 4. Migrate assets from old project (${OLD_DIR}) ==="
mkdir -p "${NEW_ROOT}/cof-resource"/{cards,assets,audio,config,assets/fonts}
if [ -d "${OLD_DIR}/cards" ]; then
  rsync -a "${OLD_DIR}/cards/" "${NEW_ROOT}/cof-resource/cards/"
fi
for f in bell.png logo.png bg1.jpg bg2.jpg bg3.jpg; do
  [ -f "${OLD_DIR}/${f}" ] && cp -f "${OLD_DIR}/${f}" "${NEW_ROOT}/cof-resource/assets/"
done
for f in ding.wav sendcard.mp3 newgame.wav endgame.wav; do
  [ -f "${OLD_DIR}/${f}" ] && cp -f "${OLD_DIR}/${f}" "${NEW_ROOT}/cof-resource/audio/" || true
done
[ -f "${OLD_DIR}/config/computerPlayers.json" ] \
  && cp -f "${OLD_DIR}/config/computerPlayers.json" "${NEW_ROOT}/cof-resource/config/"
[ -f "${OLD_DIR}/SourceHanSerifSC-VF.otf.woff2" ] \
  && cp -f "${OLD_DIR}/SourceHanSerifSC-VF.otf.woff2" "${NEW_ROOT}/cof-resource/assets/fonts/" || true

mkdir -p "${NEW_ROOT}/cof-java-boot/data"
if [ -f "${OLD_DIR}/data/state.json" ]; then
  cp -f "${OLD_DIR}/data/state.json" "${NEW_ROOT}/cof-java-boot/data/state.json"
fi

log "=== 5. Spring prod profile ==="
cat > "${NEW_ROOT}/cof-java-boot/cof-boot/src/main/resources/application-prod.yml" <<'EOF'
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/cof_db?currentSchema=public
    username: cof
    password: cof
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 2

cof:
  resource-root: /opt/cof-java/cof-resource
EOF

log "=== 6. Build backend ==="
export COF_RESOURCE_ROOT="${NEW_ROOT}/cof-resource"
cd "${NEW_ROOT}/cof-java-boot"
mvn -q -pl cof-boot -am package -DskipTests

log "=== 7. Build frontend ==="
cd "${NEW_ROOT}/cof-web-vue3"
if [ -f package-lock.json ]; then npm ci --silent; else npm install --silent; fi
npm run build

log "=== 8. One-time import: decks, computers, state.json ==="
cd "${NEW_ROOT}/cof-java-boot"
export COF_RESOURCE_ROOT="${NEW_ROOT}/cof-resource"
java -jar "$JAR" \
  --spring.profiles.active=prod \
  --import-decks \
  --import-computers \
  --migrate-state-json \
  --server.port=19002 &
IMPORT_PID=$!
sleep 90
if kill -0 "$IMPORT_PID" 2>/dev/null; then kill "$IMPORT_PID" 2>/dev/null || true; fi
sleep 2

log "=== 9. systemd: cof-boot (9002) ==="
cat > /etc/systemd/system/cof-boot.service <<EOF
[Unit]
Description=Clash of Frames API (Spring Boot)
After=network.target postgresql.service redis-server.service
Wants=postgresql.service redis-server.service

[Service]
Type=simple
WorkingDirectory=${NEW_ROOT}/cof-java-boot
Environment=COF_RESOURCE_ROOT=${NEW_ROOT}/cof-resource
Environment=SPRING_PROFILES_ACTIVE=prod
ExecStart=/usr/bin/java -jar ${JAR}
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

log "=== 10. nginx: listen 9001 -> static + proxy API ==="
cat > /etc/nginx/sites-available/cof-web-9001 <<'EOF'
server {
    listen 9001;
    listen [::]:9001;
    server_name _;

    root /opt/cof-java/cof-web-vue3/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:9002;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://127.0.0.1:9002;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
    }

    location /cards/ {
        proxy_pass http://127.0.0.1:9002;
        proxy_set_header Host $host;
    }

    location /assets/ {
        proxy_pass http://127.0.0.1:9002;
        proxy_set_header Host $host;
    }

    location /audio/ {
        proxy_pass http://127.0.0.1:9002;
        proxy_set_header Host $host;
    }
}
EOF
ln -sf /etc/nginx/sites-available/cof-web-9001 /etc/nginx/sites-enabled/cof-web-9001
nginx -t
systemctl reload nginx

systemctl daemon-reload
systemctl enable cof-boot
systemctl restart cof-boot

log "=== 11. Verify (old 9000 must still run) ==="
sleep 8
ss -tlnp | grep -E ':9000|:9001|:9002' || true
curl -sf -o /dev/null -w "old9000:%{http_code}\n" http://127.0.0.1:9000/ || echo "old9000:fail"
curl -sf -o /dev/null -w "new9001:%{http_code}\n" http://127.0.0.1:9001/ || echo "new9001:fail"
curl -sf -o /dev/null -w "api9002:%{http_code}\n" http://127.0.0.1:9002/api/v1/meta/card-libraries || echo "api9002:fail"

log "=== Done. Old: :9000  New web: :9001  New API: :9002 ==="
