#!/usr/bin/env bash
set -euo pipefail

########################################
# Configure Nginx + SSL pour api-sen-stocksaas.com (sans rebuild WAR)
#
# Depuis votre Mac :
#   ./configure-api-dns-ssl.sh
#
# Ou en une commande :
#   ssh ubuntu@164.132.43.247 'bash -s' < fix-api-ssl-remote.sh
########################################
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REMOTE_SCRIPT="${SCRIPT_DIR}/fix-api-ssl-remote.sh"

VPS_USER="${VPS_USER:-ubuntu}"
VPS_HOST="${VPS_HOST:-164.132.43.247}"
API_DOMAIN="${API_DOMAIN:-api-sen-stocksaas.com}"
LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-ablayefaye9725@gmail.com}"
BACKEND_UPSTREAM="${BACKEND_UPSTREAM:-http://127.0.0.1:8080}"

if [[ ! -f "${REMOTE_SCRIPT}" ]]; then
  echo "❌ Script distant introuvable: ${REMOTE_SCRIPT}"
  exit 1
fi

echo ">> Connexion SSH vers ${VPS_USER}@${VPS_HOST}..."
echo "   (mot de passe VPS demandé)"
echo ""

ssh "${VPS_USER}@${VPS_HOST}" env \
  "API_DOMAIN=${API_DOMAIN}" \
  "LETSENCRYPT_EMAIL=${LETSENCRYPT_EMAIL}" \
  "BACKEND_UPSTREAM=${BACKEND_UPSTREAM}" \
  bash -s < "${REMOTE_SCRIPT}"

echo ""
echo "✅ Terminé. Testez depuis votre Mac :"
echo "   curl -i https://${API_DOMAIN}/api/public/platform/status"
