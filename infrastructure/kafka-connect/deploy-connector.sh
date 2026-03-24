#!/usr/bin/env bash
# =============================================================================
# deploy-connector.sh
# =============================================================================
# Déploie le connecteur Kafka Connect FileStreamSink via l'API REST de Connect.
#
# Prérequis :
#   - Le stack docker-compose est démarré (kafka-connect est healthy)
#   - curl est disponible sur le PATH
#
# Usage :
#   chmod +x deploy-connector.sh
#   ./deploy-connector.sh
#
# Pour vérifier l'état du connecteur après déploiement :
#   curl http://localhost:8083/connectors/tuuuur-analytics-filesink/status | jq
#
# Pour supprimer le connecteur :
#   curl -X DELETE http://localhost:8083/connectors/tuuuur-analytics-filesink
# =============================================================================

set -euo pipefail

CONNECT_URL="${KAFKA_CONNECT_URL:-http://localhost:8083}"
CONNECTOR_NAME="tuuuur-analytics-filesink"
CONNECTOR_JSON="$(dirname "$0")/connectors/tuuuur-analytics-filesink.json"

echo ""
echo "══════════════════════════════════════════════════════════"
echo "  Déploiement du connecteur Kafka Connect – Tuuuur Merch"
echo "══════════════════════════════════════════════════════════"
echo ""

# ── Attente que Kafka Connect soit prêt ──────────────────────────────────────
echo "⏳  Attente de Kafka Connect sur ${CONNECT_URL} ..."
MAX_RETRIES=30
RETRY=0
until curl -s "${CONNECT_URL}/connectors" > /dev/null 2>&1; do
  RETRY=$((RETRY + 1))
  if [ $RETRY -ge $MAX_RETRIES ]; then
    echo "❌  Kafka Connect ne répond pas après ${MAX_RETRIES} tentatives. Abandon."
    exit 1
  fi
  echo "   Tentative ${RETRY}/${MAX_RETRIES} – attente 3s..."
  sleep 3
done
echo "✅  Kafka Connect prêt."
echo ""

# ── Liste des connecteurs existants ──────────────────────────────────────────
echo "📋  Connecteurs existants :"
curl -s "${CONNECT_URL}/connectors" | tr -d '[]"' | tr ',' '\n' \
  | sed 's/^/   - /' || echo "   (aucun)"
echo ""

# ── Vérifier si le connecteur existe déjà ────────────────────────────────────
STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  "${CONNECT_URL}/connectors/${CONNECTOR_NAME}")

if [ "$STATUS_CODE" = "200" ]; then
  echo "♻️   Le connecteur '${CONNECTOR_NAME}' existe déjà. Mise à jour de la config..."
  HTTP_METHOD="PUT"
  ENDPOINT="${CONNECT_URL}/connectors/${CONNECTOR_NAME}/config"
  # Pour PUT, on envoie uniquement l'objet "config" (sans le nom)
  PAYLOAD=$(python3 -c "
import json, sys
with open('${CONNECTOR_JSON}') as f:
    data = json.load(f)
print(json.dumps(data['config']))
" 2>/dev/null || jq '.config' "${CONNECTOR_JSON}")
else
  echo "➕  Création du connecteur '${CONNECTOR_NAME}'..."
  HTTP_METHOD="POST"
  ENDPOINT="${CONNECT_URL}/connectors"
  PAYLOAD=$(cat "${CONNECTOR_JSON}")
fi

# ── Déploiement via l'API REST Kafka Connect ─────────────────────────────────
RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X "${HTTP_METHOD}" \
  -H "Content-Type: application/json" \
  -d "${PAYLOAD}" \
  "${ENDPOINT}")

HTTP_STATUS=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

echo ""
echo "Réponse HTTP ${HTTP_STATUS} :"
echo "${BODY}" | python3 -m json.tool 2>/dev/null || echo "${BODY}"
echo ""

if [ "$HTTP_STATUS" = "200" ] || [ "$HTTP_STATUS" = "201" ]; then
  echo "✅  Connecteur déployé avec succès !"
  echo ""
  echo "📁  Le fichier de sortie sera écrit dans :"
  echo "    infrastructure/kafka-connect/output/tuuuur-sales-stats.txt"
  echo ""
  echo "🔍  Pour vérifier l'état :"
  echo "    curl ${CONNECT_URL}/connectors/${CONNECTOR_NAME}/status | python3 -m json.tool"
else
  echo "❌  Erreur lors du déploiement (HTTP ${HTTP_STATUS})."
  exit 1
fi
