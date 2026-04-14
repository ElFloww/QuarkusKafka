#!/bin/bash

# Script pour spammer des requêtes POST à l'endpoint d'ordres
# Utiliser: ./spam-orders.sh [nombre de requêtes] [délai en secondes]

# Paramètres
NUM_REQUESTS=${1:-100}
DELAY=${2:-0}
URL="http://localhost:8080/api/orders"

# Payload JSON
PAYLOAD='{
  "itemId": "tshirt-bronze",
  "playerId": "player-42",
  "playerName": "Joueur Anonymous",
  "playerRank": "CHALLENGER",
  "quantity": 1
}'

echo "🚀 Démarrage du spam d'ordres..."
echo "URL: $URL"
echo "Nombre de requêtes: $NUM_REQUESTS"
echo "Délai entre requêtes: ${DELAY}s"
echo "================================"

# Compteurs
success=0
failed=0
start_time=$(date +%s)

# Boucle de spam
for ((i=1; i<=NUM_REQUESTS; i++)); do
  response=$(curl -s -X POST \
    -H "Content-Type: application/json" \
    -d "$PAYLOAD" \
    "$URL" \
    -w "\n%{http_code}")
  
  http_code=$(echo "$response" | tail -n 1)
  body=$(echo "$response" | head -n -1)
  
  if [[ "$http_code" -ge 200 && "$http_code" -lt 300 ]]; then
    ((success++))
    echo "[✓ $i/$NUM_REQUESTS] Status: $http_code"
  else
    ((failed++))
    echo "[✗ $i/$NUM_REQUESTS] Status: $http_code - $body"
  fi
  
  # Délai optionnel entre les requêtes
  if [ $i -lt $NUM_REQUESTS ] && [ "$DELAY" -gt 0 ]; then
    sleep "$DELAY"
  fi
done

end_time=$(date +%s)
total_time=$((end_time - start_time))

echo "================================"
echo "📊 Résultats:"
echo "   ✓ Succès: $success"
echo "   ✗ Erreurs: $failed"
echo "   ⏱️  Temps total: ${total_time}s"
echo "   📈 Requêtes/sec: $(echo "scale=2; $NUM_REQUESTS / $total_time" | bc)"
