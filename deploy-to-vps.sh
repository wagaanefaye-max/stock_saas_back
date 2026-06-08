#!/usr/bin/env bash
set -e

########################################
# Variables à adapter une seule fois
########################################
VPS_USER="ubuntu"                # TODO: remplace par ton utilisateur SSH
VPS_HOST="51.91.58.3"          # IP / hostname du VPS
APP_NAME="stock-saas-backend"
REMOTE_DIR="/opt/${APP_NAME}"
JAR_NAME="${APP_NAME}-1.0.0.jar"   # jar généré par Maven (version 1.0.0)
SERVICE_NAME="${APP_NAME}.service"

########################################
# 1. Build du jar en local
########################################
echo ">> Build du projet Maven..."
cd "$(dirname "$0")"   # se placer dans la racine du backend
if [ -x "./mvnw" ]; then
  ./mvnw clean package -DskipTests
else
  mvn clean package -DskipTests
fi

########################################
# 2. Copie du jar sur le VPS
########################################
echo ">> Copie du jar sur le VPS..."
# On copie d'abord dans le home de l'utilisateur, puis on déplace avec sudo vers /opt
scp "target/${JAR_NAME}" ${VPS_USER}@${VPS_HOST}:"/home/${VPS_USER}/${APP_NAME}.jar"
ssh ${VPS_USER}@${VPS_HOST} "sudo mkdir -p ${REMOTE_DIR} && sudo mv /home/${VPS_USER}/${APP_NAME}.jar ${REMOTE_DIR}/${APP_NAME}.jar"

########################################
# 3. Création / mise à jour du service systemd sur le VPS
########################################
echo ">> Configuration du service systemd sur le VPS..."
ssh ${VPS_USER}@${VPS_HOST} "sudo bash -s" <<EOF
set -e

SERVICE_PATH="/etc/systemd/system/${SERVICE_NAME}"

cat > "\${SERVICE_PATH}" <<EOL
[Unit]
Description=${APP_NAME}
After=network.target

[Service]
User=www-data
WorkingDirectory=${REMOTE_DIR}
ExecStart=/usr/bin/java -jar ${REMOTE_DIR}/${APP_NAME}.jar --spring.profiles.active=prod
Restart=always
RestartSec=10
Environment=JAVA_TOOL_OPTIONS=-Xms256m -Xmx512m

[Install]
WantedBy=multi-user.target
EOL

systemctl daemon-reload
systemctl enable ${SERVICE_NAME}
systemctl restart ${SERVICE_NAME}
EOF

########################################
# 4. Afficher l'état du service
########################################
echo ">> Statut du service sur le VPS :"
ssh ${VPS_USER}@${VPS_HOST} "sudo systemctl status ${SERVICE_NAME} --no-pager -l || true"

echo "✅ Déploiement terminé."

