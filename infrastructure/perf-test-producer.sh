#!/bin/bash

# ==============================================================================
# Kafka Producer Performance Test - Test Massif
# ==============================================================================
# Ce script teste les performances du producteur Kafka avec un volume massif
# de messages pour stresser les brokers et mesurer le throughput/latency
#
# Usage: ./perf-test-producer.sh [topic] [num_messages] [message_size]
# Exemple: ./perf-test-producer.sh orders-events 1000000 1024
# Exemple: ./perf-test-producer.sh orders-events 100000 1024 ./mon-message.json
# ==============================================================================

set -e

# Couleurs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Paramètres par défaut
TOPIC="${1:-orders-events}"
NUM_MESSAGES="${2:-500000}"      # 500k messages par défaut
MESSAGE_SIZE="${3:-1024}"         # 1KB par défaut
PAYLOAD_FILE="${4:-}"             # Fichier de payload spécifique (optionnel)
# IMPORTANT: Utiliser les noms DNS internes du réseau Docker, pas localhost!
# car le producteur s'exécute DANS le container Kafka
BOOTSTRAP_SERVERS="kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092"
BATCH_SIZE="32768"                # 32KB batches pour optimiser throughput
LINGER_MS="100"                   # Wait 100ms pour grouper les batches
COMPRESSION="snappy"              # Compression pour réduire la bande passante

print_header() {
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BLUE}$1${NC}"
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_success() {
  echo -e "${GREEN}✅ $1${NC}"
}

print_info() {
  echo -e "${BLUE}ℹ️  $1${NC}"
}

print_warning() {
  echo -e "${YELLOW}⚠️  $1${NC}"
}

# Header
print_header "🚀 KAFKA PRODUCER PERFORMANCE TEST - TEST MASSIF"

print_info "Configuration du test:"
print_info "  Topic: $TOPIC"
print_info "  Nombre de messages: $NUM_MESSAGES"
print_info "  Taille des messages: ${MESSAGE_SIZE} bytes"
if [ -n "$PAYLOAD_FILE" ]; then
  print_info "  Fichier Payload: $PAYLOAD_FILE"
fi
print_info "  Bootstrap servers: $BOOTSTRAP_SERVERS"
print_info "  Compression: $COMPRESSION"
print_info "  Batch size: $BATCH_SIZE bytes"
print_info "  Linger time: ${LINGER_MS}ms"
echo ""

# Vérifier que le container Kafka est disponible
print_info "Vérification de la disponibilité du cluster Kafka..."
docker exec tuuuur-kafka-1 kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1 || {
  print_warning "Kafka ne semble pas accessible. Tentative d'attendre..."
  sleep 5
}

# Vérifier que le topic existe
print_info "Vérification de l'existence du topic '$TOPIC'..."
TOPICS=$(docker exec tuuuur-kafka-1 kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null || echo "")
if [[ ! "$TOPICS" == *"$TOPIC"* ]]; then
  print_warning "Le topic '$TOPIC' n'existe pas. Création du topic..."
  docker exec tuuuur-kafka-1 kafka-topics \
    --bootstrap-server localhost:9092 \
    --create \
    --if-not-exists \
    --topic "$TOPIC" \
    --partitions 3 \
    --replication-factor 3 \
    --config retention.ms=86400000
  print_success "Topic créé avec succès"
fi

echo ""
print_header "🔥 LANCEMENT DU TEST DE PERFORMANCE"

# Calcul de la taille totale des données
TOTAL_SIZE_MB=$((NUM_MESSAGES * MESSAGE_SIZE / 1024 / 1024))
print_info "Taille totale des données à écrire: ~${TOTAL_SIZE_MB}MB"
print_info "Temps estimé: dépend du throughput de vos brokers (2-5 min généralement)"
echo ""

# Préparation des arguments pour le test de performance
PERF_ARGS=(
  --topic "$TOPIC"
  --num-records "$NUM_MESSAGES"
  --throughput -1
  --producer-props
  "bootstrap.servers=$BOOTSTRAP_SERVERS"
  "acks=all"
  "compression.type=$COMPRESSION"
  "batch.size=$BATCH_SIZE"
  "linger.ms=$LINGER_MS"
  "buffer.memory=67108864"
  --producer.config /dev/null
)

if [ -n "$PAYLOAD_FILE" ] && [ -f "$PAYLOAD_FILE" ]; then
  print_info "Copie du fichier '$PAYLOAD_FILE' dans le conteneur Kafka..."
  CONTAINER_PAYLOAD="/tmp/custom-payload-$$.txt"
  docker cp "$PAYLOAD_FILE" "tuuuur-kafka-1:$CONTAINER_PAYLOAD"
  PERF_ARGS+=(--payload-file "$CONTAINER_PAYLOAD")
  print_info "Envoi du fichier '$PAYLOAD_FILE' avec injection d'une Clé Kafka..."
  START_TIME=$(date +%s%N)
  
  # Lancé le test de performance du producteur (outil natif)
  docker exec tuuuur-kafka-1 kafka-producer-perf-test "${PERF_ARGS[@]}"
  # On génère un UUID comme clé pour chaque ligne du fichier et on sépare avec une tabulation
  awk 'BEGIN { srand() } {
    uuid=sprintf("%04x%04x-%04x-4%03x-8%03x-%04x%04x%04x", rand()*65535, rand()*65535, rand()*65535, rand()*4095, rand()*4095, rand()*65535, rand()*65535, rand()*65535)
    printf "%s\t%s\n", uuid, $0
  }' "$PAYLOAD_FILE" | docker exec -i tuuuur-kafka-1 kafka-console-producer \
    --bootstrap-server "$BOOTSTRAP_SERVERS" \
    --topic "$TOPIC" \
    --property parse.key=true \
    --producer-property "compression.type=$COMPRESSION" \
    --producer-property "batch.size=$BATCH_SIZE" \
    --producer-property "linger.ms=$LINGER_MS" \
    --producer-property "acks=all"

  END_TIME=$(date +%s%N)
  TOTAL_TIME_S=$(echo "scale=3; ($END_TIME - $START_TIME) / 1000000000" | bc)
  if (( $(echo "$TOTAL_TIME_S <= 0" | bc -l) )); then TOTAL_TIME_S=0.001; fi
  print_success "Envoi terminé."
  print_info "⏱️  Temps total : ${TOTAL_TIME_S} secondes"
else
  print_info "Génération à la volée de $NUM_MESSAGES messages avec Clé Kafka (GUID) par message..."
  START_TIME=$(date +%s%N)

  # Pipeline direct: awk génère les données au format KEY:VALUE et l'envoie dans kafka-console-producer
  # Pipeline direct: awk génère les données au format KEY \t VALUE et l'envoie dans kafka-console-producer
  awk -v num="$NUM_MESSAGES" 'BEGIN {
    srand()
    split("tshirt-bronze tshirt-argent hoodie-or casquette-platine tshirt-diamant veste-challenger", itemIds, " ")
    split("T-Shirt Rang Bronze|T-Shirt Rang Argent|Hoodie Rang Or|Casquette Platine Edition|T-Shirt Rang Diamant|Veste Challenger Exclusive", itemNames, "|")
    split("BRONZE ARGENT OR PLATINE DIAMANT CHALLENGER", reqRanks, " ")
    split("19.99 24.99 49.99 34.99 59.99 149.99", prices, " ")
    split("BRONZE ARGENT OR PLATINE DIAMANT CHALLENGER", ranks, " ")
    split("Alex Sam Nina Leo Maya Noah Lina Eli Iris Milo", names, " ")
    
    for(i=0; i<num; i++) {
      uuid=sprintf("%04x%04x-%04x-4%03x-8%03x-%04x%04x%04x", rand()*65535, rand()*65535, rand()*65535, rand()*4095, rand()*4095, rand()*65535, rand()*65535, rand()*65535)
      
      itemIdx = int(rand()*6) + 1
      itemId = itemIds[itemIdx]
      itemName = itemNames[itemIdx]
      reqRank = reqRanks[itemIdx]
      price = prices[itemIdx]
      
      playerRank = ranks[int(rand()*6) + 1]
      qty = int(rand()*3) + 1
      player_id = sprintf("player-%04d", int(rand()*9000)+1000)
      player_name = sprintf("%s_%04d", names[int(rand()*10) + 1], int(rand()*9000)+1000)
      
      # Injection de uuid en tant que clé Kafka, séparé de la valeur par une tabulation (\t)
      printf "%s\t{\"orderId\":\"%s\",\"status\":\"CREATED\",\"playerId\":\"%s\",\"playerName\":\"%s\",\"playerRank\":\"%s\",\"itemId\":\"%s\",\"itemName\":\"%s\",\"requiredRank\":\"%s\",\"quantity\":%d,\"unitPrice\":%.2f,\"createdAt\":\"2026-05-02T14:02:04.936928Z\"}\n", uuid, uuid, player_id, player_name, playerRank, itemId, itemName, reqRank, qty, price
    }
  }' | docker exec -i tuuuur-kafka-1 kafka-console-producer \
    --bootstrap-server "$BOOTSTRAP_SERVERS" \
    --topic "$TOPIC" \
    --property parse.key=true \
    --producer-property "compression.type=$COMPRESSION" \
    --producer-property "batch.size=$BATCH_SIZE" \
    --producer-property "linger.ms=$LINGER_MS" \
    --producer-property "delivery.timeout.ms=600000" \
    --producer-property "request.timeout.ms=120000" \
    --producer-property "acks=all"

  END_TIME=$(date +%s%N)
  TOTAL_TIME_S=$(echo "scale=3; ($END_TIME - $START_TIME) / 1000000000" | bc)
  if (( $(echo "$TOTAL_TIME_S <= 0" | bc -l) )); then TOTAL_TIME_S=0.001; fi
  THROUGHPUT=$(echo "scale=2; $NUM_MESSAGES / $TOTAL_TIME_S" | bc)
  
  echo ""
  print_success "$NUM_MESSAGES records envoyés avec succès."
  print_info "⏱️  Temps total : ${TOTAL_TIME_S} secondes"
  print_info "🚀 Throughput  : ${THROUGHPUT} records/sec"
fi

echo ""
print_header "✨ TEST TERMINÉ"
print_success "Les messages ont été envoyés au topic '$TOPIC'"
print_info "Vous pouvez consulter Grafana pour voir les métriques: http://localhost:3000"
