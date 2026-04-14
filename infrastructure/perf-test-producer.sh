#!/bin/bash

# ==============================================================================
# Kafka Producer Performance Test - Test Massif
# ==============================================================================
# Ce script teste les performances du producteur Kafka avec un volume massif
# de messages pour stresser les brokers et mesurer le throughput/latency
#
# Usage: ./perf-test-producer.sh [topic] [num_messages] [message_size]
# Exemple: ./perf-test-producer.sh orders-events 1000000 1024
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

# Lancé le test de performance du producteur
# Le output sera parsé pour extraire les métriques
docker exec tuuuur-kafka-1 kafka-producer-perf-test \
  --topic "$TOPIC" \
  --num-records "$NUM_MESSAGES" \
  --record-size "$MESSAGE_SIZE" \
  --throughput -1 \
  --producer-props \
    bootstrap.servers="$BOOTSTRAP_SERVERS" \
    acks=all \
    compression.type="$COMPRESSION" \
    batch.size="$BATCH_SIZE" \
    linger.ms="$LINGER_MS" \
    buffer.memory=67108864 \
  --producer.config /dev/null

echo ""
print_header "✨ TEST TERMINÉ"
print_success "Les messages ont été envoyés au topic '$TOPIC'"
print_info "Vous pouvez consulter Grafana pour voir les métriques: http://localhost:3000"
