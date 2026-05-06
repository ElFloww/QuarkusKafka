#!/bin/bash

# Script ULTRA-RAPIDE pour spammer l'API à mort avec parallélisation
# Utiliser: ./spam-orders.sh [nombre de requêtes] [nombre de workers parallèles]

# Paramètres
NUM_REQUESTS=${1:-1000}
PARALLEL_WORKERS=${2:-100}  # 100 requêtes en parallèle = dévastation 💥
URL="http://localhost:8080/api/orders"

# Donnees aleatoires pour chaque requete
ITEM_IDS=(
  "tshirt-bronze"
  "tshirt-argent"
  "hoodie-or"
  "casquette-platine"
  "tshirt-diamant"
  "veste-challenger"
)

PLAYER_RANKS=(
  "BRONZE"
  "ARGENT"
  "OR"
  "PLATINE"
  "DIAMANT"
  "CHALLENGER"
)

PLAYER_NAMES=(
  "Alex"
  "Sam"
  "Nina"
  "Leo"
  "Maya"
  "Noah"
  "Lina"
  "Eli"
  "Iris"
  "Milo"
)

echo "🚀🚀🚀 SPAM À MORT DE L'API 🚀🚀🚀"
echo "URL: $URL"
echo "Nombre de requêtes: $NUM_REQUESTS"
echo "Workers parallèles: $PARALLEL_WORKERS"
echo "Objectif: $((NUM_REQUESTS * PARALLEL_WORKERS / 10)) req/sec minimum"
echo "================================"

# Compteurs
success=0
failed=0
start_time=$(date +%s%N)  # Nanoseconde pour précision

# Fonction pour envoyer une requête
send_request() {
  local request_num=$1
  
  item_id=${ITEM_IDS[$((RANDOM % ${#ITEM_IDS[@]}))]}
  player_rank=${PLAYER_RANKS[$((RANDOM % ${#PLAYER_RANKS[@]}))]}
  player_name_base=${PLAYER_NAMES[$((RANDOM % ${#PLAYER_NAMES[@]}))]}
  player_name="${player_name_base}_$((1000 + RANDOM % 9000))"
  player_id="player-$((1000 + RANDOM % 9000))"
  quantity=$((1 + RANDOM % 3))

  payload=$(cat <<EOF
{
  "itemId": "$item_id",
  "playerId": "$player_id",
  "playerName": "$player_name",
  "playerRank": "$player_rank",
  "quantity": $quantity
}
EOF
)

  # Requête silencieuse, juste le code HTTP
  http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "$URL")
  
  if [[ "$http_code" -ge 200 && "$http_code" -lt 300 ]]; then
    echo "✓ $request_num: $http_code | item=$item_id | rank=$player_rank | qty=$quantity"
  else
    echo "✗ $request_num: $http_code | item=$item_id"
  fi
}

# Export de la fonction et variables pour background jobs
export -f send_request
export ITEM_IDS PLAYER_RANKS PLAYER_NAMES URL

# Lancer les requêtes en parallèle
echo "Lancement de $NUM_REQUESTS requêtes en groupes de $PARALLEL_WORKERS..."

for ((i=1; i<=NUM_REQUESTS; i++)); do
  # Lancer la requête en arrière-plan
  send_request $i &
  
  # Limiter à PARALLEL_WORKERS jobs simultanés
  if (( i % PARALLEL_WORKERS == 0 )); then
    echo "  ⏳ $i/$NUM_REQUESTS requêtes lancées..."
    wait  # Attendre que les PARALLEL_WORKERS jobs finissent avant de continuer
  fi
done

# Attendre les dernières requêtes
wait

end_time=$(date +%s%N)
total_time_ns=$((end_time - start_time))
total_time_s=$(echo "scale=3; $total_time_ns / 1000000000" | bc)

if (( $(echo "$total_time_s <= 0" | bc -l) )); then
  total_time_s=0.001
fi

throughput=$(echo "scale=2; $NUM_REQUESTS / $total_time_s" | bc)

echo ""
echo "================================"
echo "🔥 RÉSULTATS DU CARNAGE:"
echo "   ⏱️  Temps total: ${total_time_s}s"
echo "   📈 Throughput: ${throughput} req/sec"
echo "   🎯 Requêtes envoyées: $NUM_REQUESTS"
echo "================================"
