#!/bin/bash

# ==============================================================================
# Kafka Consumer Performance Test - Test Massif
# ==============================================================================
# Ce script teste les performances du consommateur Kafka en lisant les messages
# depuis le début et en mesurant le throughput et latency
#
# Usage: ./perf-test-consumer.sh [topic] [num_messages]
# Exemple: ./perf-test-consumer.sh orders-events 500000
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
NUM_MESSAGES="${2:-500000}"
# IMPORTANT: Utiliser les noms DNS internes du réseau Docker, pas localhost!
# car le consommateur s'exécute DANS le container Kafka
BOOTSTRAP_SERVERS="kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092"
CONSUMER_GROUP="perf-test-consumer-$(date +%s)"  # Groupe unique pour chaque test

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
print_header "🔍 KAFKA CONSUMER PERFORMANCE TEST - TEST MASSIF"

print_info "Configuration du test:"
print_info "  Topic: $TOPIC"
print_info "  Nombre de messages à consommer: $NUM_MESSAGES"
print_info "  Bootstrap servers: $BOOTSTRAP_SERVERS"
print_info "  Groupe de consommateurs: $CONSUMER_GROUP"
echo ""

# Vérifier que le container Kafka est disponible
print_info "Vérification de la disponibilité du cluster Kafka..."
docker exec tuuuur-kafka-1 kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1 || {
  print_warning "Kafka ne semble pas accessible. Tentative d'attendre..."
  sleep 5
}

# Vérifier que le topic existe et obtenir le nombre de messages
print_info "Vérification du topic '$TOPIC'..."
TOPICS=$(docker exec tuuuur-kafka-1 kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null || echo "")
if [[ ! "$TOPICS" == *"$TOPIC"* ]]; then
  print_warning "Le topic '$TOPIC' n'existe pas!"
  exit 1
fi

# Compter les messages disponibles
print_info "Comptage des messages disponibles dans le topic..."
MESSAGE_COUNT=$(docker exec tuuuur-kafka-1 kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic "$TOPIC" 2>/dev/null | awk -F':' '{total+=$3} END {print total}' || echo "0")

if [ "$MESSAGE_COUNT" -eq 0 ]; then
  print_warning "Aucun message trouvé dans le topic '$TOPIC'"
  print_info "Assurez-vous d'avoir d'abord lancé le test producteur!"
  exit 1
fi

print_success "Messages trouvés dans le topic: $MESSAGE_COUNT"
echo ""

print_header "🔥 LANCEMENT DU TEST DE CONSOMMATION"
print_info "Consommation depuis le début du topic (earliest)..."
echo ""

# Lancer le test de performance du consommateur
# --from-latest: Consommer depuis le début
# --messages: Nombre de messages à consommer
docker exec tuuuur-kafka-1 kafka-consumer-perf-test \
  --broker-list "$BOOTSTRAP_SERVERS" \
  --topic "$TOPIC" \
  --messages "$NUM_MESSAGES" \
  --threads 1 \
  --reporting-interval 50000 \
  --show-detailed-stats

echo ""
print_header "✨ TEST DE CONSOMMATION TERMINÉ"
print_success "Les messages ont été consommés depuis le topic '$TOPIC'"
print_info "Groupe de consommateurs utilisé: $CONSUMER_GROUP"
print_info "Vous pouvez consulter Grafana pour voir les métriques: http://localhost:3000"
