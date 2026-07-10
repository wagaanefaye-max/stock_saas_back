#!/usr/bin/env bash
set -euo pipefail

########################################
# Déploiement BACKEND (WAR) sur Tomcat 10 — VPS
# Usage:
#   ./deploy-backend-to-vps.sh
# ou:
#   VPS_HOST=164.132.43.247 API_DOMAIN=api-sen-stocksaas.com ./deploy-backend-to-vps.sh
########################################
VPS_USER="${VPS_USER:-ubuntu}"
VPS_HOST="${VPS_HOST:-164.132.43.247}"
API_DOMAIN="${API_DOMAIN:-api-sen-stocksaas.com}"
FRONTEND_DOMAIN="${FRONTEND_DOMAIN:-sen-stocksaas.com}"
WWW_DOMAIN="${WWW_DOMAIN:-www.sen-stocksaas.com}"
LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-ablayefaye9725@gmail.com}"
BACKEND_UPSTREAM="${BACKEND_UPSTREAM:-http://127.0.0.1:8080}"
NGINX_API_SITE_NAME="${NGINX_API_SITE_NAME:-${API_DOMAIN}}"
APP_NAME="${APP_NAME:-stock-saas-backend}"
APP_VERSION="${APP_VERSION:-1.0.0}"
WAR_NAME="${WAR_NAME:-${APP_NAME}-${APP_VERSION}.war}"
TOMCAT_SERVICE="${TOMCAT_SERVICE:-tomcat10}"
TOMCAT_WEBAPPS="${TOMCAT_WEBAPPS:-/var/lib/tomcat10/webapps}"
TOMCAT_SETENV="${TOMCAT_SETENV:-/usr/share/tomcat10/bin/setenv.sh}"
LEGACY_SYSTEMD_SERVICE="${LEGACY_SYSTEMD_SERVICE:-${APP_NAME}.service}"
DB_URL="${DB_URL:-jdbc:postgresql://164.132.43.247:5432/stock_saas_db}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-Nevrose1997!!}"
JAVA_XMS="${JAVA_XMS:-256m}"
JAVA_XMX="${JAVA_XMX:-768m}"
UPLOAD_SUBSCRIPTIONS_DIR="${UPLOAD_SUBSCRIPTIONS_DIR:-/home/ubuntu/justificatifs}"
UPLOAD_COMPANIES_LOGOS_DIR="${UPLOAD_COMPANIES_LOGOS_DIR:-/home/ubuntu/company-logos}"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
LOCAL_WAR="${APP_DIR}/target/${WAR_NAME}"

echo ">> DNS requis chez votre registrar (avant Let's Encrypt) :"
echo "   Type A   ${API_DOMAIN}  ->  ${VPS_HOST}"
echo "   (ou CNAME ${API_DOMAIN}  ->  ${FRONTEND_DOMAIN})"
echo ""

########################################
# 1. Build WAR en local
########################################
echo ">> Build du backend Maven (WAR, profil prod)..."
cd "${APP_DIR}"

if [[ -x "./mvnw" ]]; then
  ./mvnw clean package -DskipTests
else
  if ! command -v mvn >/dev/null 2>&1; then
    echo "❌ Maven introuvable. Installe Maven ou ajoute ./mvnw au projet."
    exit 1
  fi
  mvn clean package -DskipTests
fi

if [[ ! -f "${LOCAL_WAR}" ]]; then
  echo "❌ WAR introuvable: ${LOCAL_WAR}"
  exit 1
fi

########################################
# 2. Copie du WAR sur le VPS
########################################
echo ">> Copie du WAR sur le VPS..."
scp "${LOCAL_WAR}" "${VPS_USER}@${VPS_HOST}:/tmp/${WAR_NAME}"

########################################
# 3. Installation Tomcat + déploiement
########################################
echo ">> Configuration Tomcat + Nginx API sur le VPS..."
ssh "${VPS_USER}@${VPS_HOST}" env \
  "APP_NAME=${APP_NAME}" \
  "WAR_NAME=${WAR_NAME}" \
  "TOMCAT_SERVICE=${TOMCAT_SERVICE}" \
  "TOMCAT_WEBAPPS=${TOMCAT_WEBAPPS}" \
  "TOMCAT_SETENV=${TOMCAT_SETENV}" \
  "LEGACY_SYSTEMD_SERVICE=${LEGACY_SYSTEMD_SERVICE}" \
  "DB_URL=${DB_URL}" \
  "DB_USERNAME=${DB_USERNAME}" \
  "DB_PASSWORD=${DB_PASSWORD}" \
  "JAVA_XMS=${JAVA_XMS}" \
  "JAVA_XMX=${JAVA_XMX}" \
  "UPLOAD_SUBSCRIPTIONS_DIR=${UPLOAD_SUBSCRIPTIONS_DIR}" \
  "UPLOAD_COMPANIES_LOGOS_DIR=${UPLOAD_COMPANIES_LOGOS_DIR}" \
  "API_DOMAIN=${API_DOMAIN}" \
  "FRONTEND_DOMAIN=${FRONTEND_DOMAIN}" \
  "WWW_DOMAIN=${WWW_DOMAIN}" \
  "LETSENCRYPT_EMAIL=${LETSENCRYPT_EMAIL}" \
  "BACKEND_UPSTREAM=${BACKEND_UPSTREAM}" \
  "NGINX_API_SITE_NAME=${NGINX_API_SITE_NAME}" \
  bash -s <<'EOF'
set -eo pipefail

: "${WAR_NAME:?WAR_NAME manquant}"
: "${TOMCAT_WEBAPPS:?TOMCAT_WEBAPPS manquant}"
TOMCAT_SERVICE="${TOMCAT_SERVICE:-tomcat10}"
TOMCAT_SETENV="${TOMCAT_SETENV:-/usr/share/tomcat10/bin/setenv.sh}"
LEGACY_SYSTEMD_SERVICE="${LEGACY_SYSTEMD_SERVICE:-stock-saas-backend.service}"
UPLOAD_SUBSCRIPTIONS_DIR="${UPLOAD_SUBSCRIPTIONS_DIR:-/home/ubuntu/justificatifs}"
UPLOAD_COMPANIES_LOGOS_DIR="${UPLOAD_COMPANIES_LOGOS_DIR:-/home/ubuntu/company-logos}"
JAVA_XMS="${JAVA_XMS:-256m}"
JAVA_XMX="${JAVA_XMX:-768m}"
JAVA_OPTS="-Xms${JAVA_XMS} -Xmx${JAVA_XMX}"
API_DOMAIN="${API_DOMAIN:-api-sen-stocksaas.com}"
FRONTEND_DOMAIN="${FRONTEND_DOMAIN:-sen-stocksaas.com}"
WWW_DOMAIN="${WWW_DOMAIN:-www.sen-stocksaas.com}"
LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-}"
BACKEND_UPSTREAM="${BACKEND_UPSTREAM:-http://127.0.0.1:8080}"
NGINX_API_SITE_NAME="${NGINX_API_SITE_NAME:-${API_DOMAIN}}"
API_SITE_PATH="/etc/nginx/sites-available/${NGINX_API_SITE_NAME}"
CORS_ORIGINS="https://${FRONTEND_DOMAIN},https://${WWW_DOMAIN},https://${API_DOMAIN}"

sudo apt-get update -y
sudo apt-get install -y openjdk-21-jdk tomcat10 nginx certbot python3-certbot-nginx curl

# Ancien déploiement jar/systemd (port 8080) : arrêt pour éviter conflit
sudo systemctl stop "${LEGACY_SYSTEMD_SERVICE}" 2>/dev/null || true
sudo systemctl disable "${LEGACY_SYSTEMD_SERVICE}" 2>/dev/null || true

sudo mkdir -p "${UPLOAD_SUBSCRIPTIONS_DIR}" "${UPLOAD_COMPANIES_LOGOS_DIR}"
sudo chown -R tomcat:tomcat "${UPLOAD_SUBSCRIPTIONS_DIR}" "${UPLOAD_COMPANIES_LOGOS_DIR}"

sudo tee "${TOMCAT_SETENV}" >/dev/null <<SETENV
#!/bin/sh
export JAVA_OPTS="${JAVA_OPTS} -Dspring.profiles.active=prod"
export SPRING_DATASOURCE_URL="${DB_URL}"
export SPRING_DATASOURCE_USERNAME="${DB_USERNAME}"
export SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}"
export APP_UPLOAD_SUBSCRIPTIONS_DIR="${UPLOAD_SUBSCRIPTIONS_DIR}"
export APP_UPLOAD_COMPANIES_LOGOS_DIR="${UPLOAD_COMPANIES_LOGOS_DIR}"
export AUTH_COOKIE_SECURE="true"
export AUTH_COOKIE_SAME_SITE="None"
export AUTH_COOKIE_DOMAIN=".sen-stocksaas.com"
export APP_BASE_URL="https://${FRONTEND_DOMAIN}"
export APP_CORS_ALLOWED_ORIGINS="${CORS_ORIGINS}"
SETENV
sudo chmod 755 "${TOMCAT_SETENV}"

sudo systemctl stop "${TOMCAT_SERVICE}" || true
sudo rm -rf "${TOMCAT_WEBAPPS}/ROOT" "${TOMCAT_WEBAPPS}/ROOT.war"
sudo mv "/tmp/${WAR_NAME}" "${TOMCAT_WEBAPPS}/ROOT.war"
sudo chown tomcat:tomcat "${TOMCAT_WEBAPPS}/ROOT.war"

sudo systemctl enable "${TOMCAT_SERVICE}"
sudo systemctl restart "${TOMCAT_SERVICE}"

sudo tee "${API_SITE_PATH}" >/dev/null <<CONF
server {
    listen 80;
    listen [::]:80;
    server_name ${API_DOMAIN};

    client_max_body_size 10M;

    location / {
        proxy_pass ${BACKEND_UPSTREAM};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 120s;
    }
}
CONF

sudo ln -sf "${API_SITE_PATH}" "/etc/nginx/sites-enabled/${NGINX_API_SITE_NAME}"
sudo nginx -t
sudo systemctl enable nginx
sudo systemctl reload nginx

if [[ -n "${LETSENCRYPT_EMAIL}" ]]; then
  if getent hosts "${API_DOMAIN}" >/dev/null 2>&1; then
    echo ">> Certificat SSL pour ${API_DOMAIN}..."
    if ! sudo certbot --nginx --non-interactive --agree-tos \
      -m "${LETSENCRYPT_EMAIL}" \
      -d "${API_DOMAIN}" \
      --redirect; then
      echo "⚠️ Certbot API a échoué. Relancez: ./configure-api-dns-ssl.sh"
    fi
    sudo nginx -t
    sudo systemctl reload nginx
  else
    echo "⚠️ DNS ${API_DOMAIN} non résolu — ajoutez l'enregistrement A vers ce VPS puis relancez certbot."
  fi
else
  echo "⚠️ LETSENCRYPT_EMAIL non fourni: SSL API ignoré."
fi

echo ">> Attente du démarrage Tomcat..."
API_OK=0
for i in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:8080/api/public/platform/status" >/dev/null 2>&1; then
    API_OK=1
    break
  fi
  sleep 2
done

if [[ "${API_OK}" -eq 1 ]]; then
  echo "✅ API Tomcat: http://127.0.0.1:8080/api/"
  if curl -fsS "http://${API_DOMAIN}/api/public/platform/status" >/dev/null 2>&1; then
    echo "✅ API publique: http://${API_DOMAIN}/api/"
  elif curl -fsSk "https://${API_DOMAIN}/api/public/platform/status" >/dev/null 2>&1; then
    echo "✅ API publique: https://${API_DOMAIN}/api/"
  else
    echo "⚠️ Nginx OK mais ${API_DOMAIN} ne répond pas encore (vérifiez le DNS)."
  fi
else
  echo "⚠️ Tomcat démarré mais l'API ne répond pas encore. Vérifie les logs:"
  echo "   sudo journalctl -u ${TOMCAT_SERVICE} -n 80 --no-pager"
  echo "   sudo tail -n 80 /var/log/tomcat10/catalina.out"
fi
EOF

echo "✅ Backend déployé — Tomcat ${VPS_HOST}:8080 | API https://${API_DOMAIN}/api/"
