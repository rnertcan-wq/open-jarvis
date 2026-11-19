#!/usr/bin/env bash
set -Eeuo pipefail

REPO_RAW="https://raw.githubusercontent.com/rnertcan-wq/open-jarvis/jarvis-ai-apk"
EXPECTED_SERVER_IP="45.141.150.148"
DOMAIN="${1:-jarvis.gurzsmp.com.tr}"
PROJECT_DIR="/opt/jarvis-ai"
COMPOSE_FILE="docker-compose.prod.yml"

log() { printf '\n[JARVIS] %s\n' "$*"; }
die() { printf '\n[JARVIS][HATA] %s\n' "$*" >&2; exit 1; }

[[ "${EUID}" -eq 0 ]] || die "Bu kurulum root yetkisiyle çalıştırılmalı. Örnek: sudo bash install.sh jarvis.gurzsmp.com.tr"
[[ "$DOMAIN" =~ ^[A-Za-z0-9.-]+$ ]] || die "Geçersiz alan adı: $DOMAIN"

for bin in curl tar base64 openssl systemctl; do command -v "$bin" >/dev/null 2>&1 || die "$bin bulunamadı."; done
command -v docker >/dev/null 2>&1 || die "Docker kurulu değil. Mevcut sunucudaki Docker kurulumunu kontrol et."
docker compose version >/dev/null 2>&1 || die "Docker Compose eklentisi bulunamadı."

log "Alan adı DNS kontrolü: $DOMAIN"
DNS_IPS="$(getent ahostsv4 "$DOMAIN" 2>/dev/null | awk '{print $1}' | sort -u || true)"
[[ -n "$DNS_IPS" ]] || die "$DOMAIN için IPv4 DNS kaydı bulunamadı. DNS panelinde A kaydı oluştur: $DOMAIN -> $EXPECTED_SERVER_IP. DNS yayıldıktan sonra komutu yeniden çalıştır."
if ! grep -Fxq "$EXPECTED_SERVER_IP" <<< "$DNS_IPS"; then
  log "Alan adı şu IPv4 adreslerine çözülüyor: $(tr '\n' ' ' <<< "$DNS_IPS")"
  log "Cloudflare proxy kullanıyorsan HTTP-01 doğrulaması yine de çalışabilir; sertifika aşamasında doğrulanacak."
fi

log "Gerekli sistem paketleri kontrol ediliyor"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y nginx certbot python3-certbot-nginx ca-certificates
systemctl enable --now nginx

log "JARVIS AI kaynakları indiriliyor"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
PARTS=(part1.b64 part2.b64 part3.b64 part4.b64 part4b.b64 part5.b64)
: > "$TMP_DIR/source.b64"
for part in "${PARTS[@]}"; do
  curl --fail --silent --show-error --location "$REPO_RAW/jarvis-ai-source/$part" >> "$TMP_DIR/source.b64"
done
base64 --decode "$TMP_DIR/source.b64" > "$TMP_DIR/source.tar.gz"
tar -tzf "$TMP_DIR/source.tar.gz" >/dev/null || die "Kaynak arşivi doğrulanamadı."
mkdir -p "$PROJECT_DIR"
tar -xzf "$TMP_DIR/source.tar.gz" -C "$PROJECT_DIR" --strip-components=1
curl --fail --silent --show-error --location "$REPO_RAW/docker-compose.prod.yml" -o "$PROJECT_DIR/$COMPOSE_FILE"

if [[ ! -f "$PROJECT_DIR/.env" ]] || grep -Eq 'change-this|replace-with' "$PROJECT_DIR/.env"; then
  if [[ -f "$PROJECT_DIR/.env" ]]; then
    cp "$PROJECT_DIR/.env" "$PROJECT_DIR/.env.example.backup.$(date +%Y%m%d%H%M%S)"
  fi
  POSTGRES_PASSWORD="$(openssl rand -hex 32)"
  ADMIN_PASSWORD="$(openssl rand -hex 18)"
  JWT_SECRET="$(openssl rand -hex 48)"
  CREDENTIAL_ENCRYPTION_KEY="$(openssl rand -base64 32)"
  cat > "$PROJECT_DIR/.env" <<EOF
NODE_ENV=production
PORT=8080
POSTGRES_PASSWORD=$POSTGRES_PASSWORD
DATABASE_URL=postgres://jarvis:$POSTGRES_PASSWORD@postgres:5432/jarvis_ai
REDIS_URL=redis://redis:6379
ADMIN_EMAIL=owner@jarvis.local
ADMIN_PASSWORD=$ADMIN_PASSWORD
JWT_SECRET=$JWT_SECRET
JWT_EXPIRES_IN=7d
CREDENTIAL_ENCRYPTION_KEY=$CREDENTIAL_ENCRYPTION_KEY
OPENAI_API_KEY=
ANTHROPIC_API_KEY=
GEMINI_API_KEY=
DEFAULT_PROVIDER=openai
DEFAULT_MODEL=gpt-6.2-astra
CORS_ORIGIN=*
EOF
  chmod 600 "$PROJECT_DIR/.env"
  printf '%s\n' "$ADMIN_PASSWORD" > "$PROJECT_DIR/.initial-admin-password"
  chmod 600 "$PROJECT_DIR/.initial-admin-password"
  NEW_ADMIN_PASSWORD="$ADMIN_PASSWORD"
else
  chmod 600 "$PROJECT_DIR/.env"
  NEW_ADMIN_PASSWORD=""
fi

cd "$PROJECT_DIR"
log "JARVIS PostgreSQL ve Redis servisleri başlatılıyor (mevcut Pterodactyl/MariaDB servislerine dokunulmaz)"
docker compose -p jarvis-ai -f "$COMPOSE_FILE" build api
docker compose -p jarvis-ai -f "$COMPOSE_FILE" up -d postgres redis

log "PostgreSQL hazır olması bekleniyor"
DB_READY=0
for _ in $(seq 1 60); do
  if docker exec jarvis-postgres pg_isready -U jarvis -d jarvis_ai >/dev/null 2>&1; then DB_READY=1; break; fi
  sleep 2
done
[[ "$DB_READY" -eq 1 ]] || die "PostgreSQL 120 saniyede hazır olmadı. docker logs jarvis-postgres komutuyla kontrol et."

log "Veritabanı şeması uygulanıyor"
docker compose -p jarvis-ai -f "$COMPOSE_FILE" run --rm --no-deps api npm run migrate
docker compose -p jarvis-ai -f "$COMPOSE_FILE" up -d api

log "Nginx ters proxy yapılandırılıyor"
NGINX_SITE="/etc/nginx/sites-available/jarvis-ai"
cat > "$NGINX_SITE" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    client_max_body_size 10m;
    location / {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 180s;
        proxy_send_timeout 180s;
        proxy_buffering off;
    }
}
EOF
ln -sfn "$NGINX_SITE" /etc/nginx/sites-enabled/jarvis-ai
nginx -t
systemctl reload nginx

if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q 'Status: active'; then
  log "HTTPS için yalnızca gerekli 80/443 web portları açılıyor; API 18080, PostgreSQL ve Redis dışarıya açılmıyor"
  ufw allow 80/tcp >/dev/null
  ufw allow 443/tcp >/dev/null
fi

log "Let's Encrypt TLS sertifikası alınıyor"
certbot --nginx --non-interactive --agree-tos --register-unsafely-without-email --redirect -d "$DOMAIN"

log "API sağlık kontrolü"
API_READY=0
for _ in $(seq 1 30); do
  if curl --fail --silent --show-error "https://$DOMAIN/health" >/dev/null 2>&1; then API_READY=1; break; fi
  sleep 2
done
[[ "$API_READY" -eq 1 ]] || die "HTTPS sağlık kontrolü başarısız. Nginx: journalctl -u nginx -n 80; API: docker logs jarvis-api"

log "JARVIS AI canlıya alındı"
printf '\nAPI adresi: https://%s\n' "$DOMAIN"
printf 'Yönetici e-postası: owner@jarvis.local\n'
if [[ -n "$NEW_ADMIN_PASSWORD" ]]; then
  printf 'Yönetici parolası: %s\n' "$NEW_ADMIN_PASSWORD"
  printf 'Parola ayrıca %s/.initial-admin-password dosyasına kaydedildi.\n' "$PROJECT_DIR"
else
  printf 'Yönetici parolası: mevcut kurulumdaki parola korunmuştur.\n'
fi
printf '\nAndroid uygulamasında Ayarlar > Sunucu bağlantısı bölümüne API adresini ve yönetici bilgilerini gir.\n'
printf 'Sonra Ayarlar > API anahtarları bölümünden OpenAI/Claude/Gemini kişisel anahtarını kaydet.\n'
