#!/usr/bin/env bash
# Script distant — à lancer depuis votre Mac :
#   ssh ubuntu@164.132.43.247 'bash -s' < fix-api-ssl-remote.sh
set -eo pipefail

API_DOMAIN="${API_DOMAIN:-api-sen-stocksaas.com}"
LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-ablayefaye9725@gmail.com}"
BACKEND_UPSTREAM="${BACKEND_UPSTREAM:-http://127.0.0.1:8080}"
API_SITE="/etc/nginx/sites-available/${API_DOMAIN}"

echo ">> [1/4] Paquets..."
sudo apt-get update -y
sudo apt-get install -y nginx certbot python3-certbot-nginx curl

echo ">> [2/4] Vhost Nginx API (${API_DOMAIN})..."
sudo tee "${API_SITE}" >/dev/null <<CONF
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

sudo ln -sf "${API_SITE}" "/etc/nginx/sites-enabled/${API_DOMAIN}"
sudo nginx -t
sudo systemctl reload nginx

echo ">> [3/4] Test Tomcat (port 8080)..."
if curl -fsS "${BACKEND_UPSTREAM}/api/public/platform/status" >/dev/null 2>&1; then
  echo "✅ Tomcat répond sur ${BACKEND_UPSTREAM}"
else
  echo "⚠️ Tomcat ne répond pas encore sur ${BACKEND_UPSTREAM}"
  echo "   Vérifiez: sudo systemctl status tomcat10"
  echo "   Logs: sudo tail -n 50 /var/log/tomcat10/catalina.out"
fi

echo ">> [3b/4] Test Nginx API (port 80)..."
if curl -fsS "http://127.0.0.1/api/public/platform/status" -H "Host: ${API_DOMAIN}" >/dev/null 2>&1; then
  echo "✅ Nginx proxy API OK"
else
  echo "⚠️ Nginx ne proxy pas encore ${API_DOMAIN} (peut être normal avant reload)"
fi

echo ">> [4/4] Certificat SSL Let's Encrypt..."
sudo certbot --nginx --non-interactive --agree-tos \
  -m "${LETSENCRYPT_EMAIL}" \
  -d "${API_DOMAIN}" \
  --redirect

sudo nginx -t
sudo systemctl reload nginx

echo ""
echo ">> Vérification certificat..."
echo | openssl s_client -connect "${API_DOMAIN}:443" -servername "${API_DOMAIN}" 2>/dev/null \
  | openssl x509 -noout -subject -ext subjectAltName

echo ""
curl -fsSI "https://${API_DOMAIN}/api/public/platform/status" | head -8
echo "✅ https://${API_DOMAIN}/api/ est prêt."
