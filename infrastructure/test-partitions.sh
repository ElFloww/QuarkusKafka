#!/bin/bash

# ============================================================
# 🧪 test-partitions.sh – Script de test du Consumer Groupe
# ============================================================
# 
# Usage: ./infrastructure/test-partitions.sh [command]
# Commands:
#   status      : Vérifier l'état des partitions (API)
#   send-one    : Envoyer une seule commande de test
#   send-bulk   : Envoyer 100 commandes
#   kafka-list  : Lister les consumer groups (CLI Kafka)
#   kafka-desc  : Décrire le consumer group
#   reset       : Réinitialiser les offsets ⚠️
#   health      : Vérifier health de l'instance
#   help        : Afficher cette aide
#

set -e

# Configuration
KAFKA_BOOTSTRAP="localhost:9092"
KAFKA_CONTAINER="tuuuur-kafka"
QUARKUS_URL="http://localhost:8080"
GROUP_ID="orders-partition-processor-grp"
TOPIC="orders-events"

# Couleurs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ──────────────────────────────────────────────────────────
# Fonctions
# ──────────────────────────────────────────────────────────

print_header() {
  echo -e "${BLUE}▼▼▼ $1 ▼▼▼${NC}"
}

print_success() {
  echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
  echo -e "${RED}❌ $1${NC}"
}

print_info() {
  echo -e "${BLUE}ℹ️  $1${NC}"
}

print_warning() {
  echo -e "${YELLOW}⚠️  $1${NC}"
}

# ──────────────────────────────────────────────────────────
# Commandes
# ──────────────────────────────────────────────────────────

cmd_status() {
  print_header "Vérifier l'état des partitions (API REST)"
  
  if ! curl -s "$QUARKUS_URL/api/partitions/status" > /dev/null; then
    print_error "Quarkus est-il démarré sur $QUARKUS_URL ?"
    return 1
  fi
  
  print_info "📊 Statut des partitions :"
  curl -s "$QUARKUS_URL/api/partitions/status" | jq .
  
  print_info "📊 Count :"
  curl -s "$QUARKUS_URL/api/partitions/count" | jq .
  
  print_info "📊 Assigned :"
  curl -s "$QUARKUS_URL/api/partitions/assigned" | jq .
}

cmd_send_one() {
  print_header "Envoyer une commande de test"
  
  PLAYER_ID="player-$(date +%s%N | cut -b1-13)"
  PLAYER_NAME="TestPlayer-$(shuf -i 1000-9999 -n 1)"
  
  print_info "Envoi de la commande ..."
  print_info "  Player ID: $PLAYER_ID"
  print_info "  Player Name: $PLAYER_NAME"
  
  RESPONSE=$(curl -s -X POST "$QUARKUS_URL/api/orders/place" \
    -H "Content-Type: application/json" \
    -d "{
      \"playerId\": \"$PLAYER_ID\",
      \"playerName\": \"$PLAYER_NAME\",
      \"playerRank\": \"GOLD\",
      \"itemId\": \"sword-001\",
      \"quantity\": 1
    }")
  
  if echo "$RESPONSE" | jq . &>/dev/null; then
    print_success "Commande envoyée !"
    echo "$RESPONSE" | jq .
  else
    print_error "Erreur lors de l'envoi : $RESPONSE"
    return 1
  fi
}

cmd_send_bulk() {
  print_header "Envoyer 100 commandes en stress test"
  
  COUNT=100000
  TOTAL=0
  
  print_info "Envoi de $COUNT commandes ..."
  
  for i in $(seq 1 $COUNT); do
    PLAYER_ID="player-bulk-$i-$(date +%s%N | cut -b1-13)"
    PLAYER_NAME="BulkPlayer$i"
    
    curl -s -X POST "$QUARKUS_URL/api/orders/place" \
      -H "Content-Type: application/json" \
      -d "{
        \"playerId\": \"$PLAYER_ID\",
        \"playerName\": \"$PLAYER_NAME\",
        \"playerRank\": \"GOLD\",
        \"itemId\": \"sword-001\",
        \"quantity\": 1
      }" > /dev/null 2>&1 &
    
    TOTAL=$((TOTAL + 1))
    
    if [ $((i % 10)) -eq 0 ]; then
      echo -ne "\r  Envoyé $i/$COUNT... "
    fi
  done
  
  echo ""
  print_success "Toutes les commandes envoyées !"
  print_info "Attendre quelques secondes pour voir les logs..."
}

cmd_kafka_list() {
  print_header "Lister les consumer groups Kafka"
  
  if ! docker inspect $KAFKA_CONTAINER > /dev/null 2>&1; then
    print_error "Conteneur Kafka '$KAFKA_CONTAINER' non trouvé"
    return 1
  fi
  
  print_info "Consumer groups actifs :"
  docker exec -it $KAFKA_CONTAINER kafka-consumer-groups \
    --bootstrap-server $KAFKA_BOOTSTRAP \
    --list
}

cmd_kafka_describe() {
  print_header "Décrire le consumer group : $GROUP_ID"
  
  if ! docker inspect $KAFKA_CONTAINER > /dev/null 2>&1; then
    print_error "Conteneur Kafka '$KAFKA_CONTAINER' non trouvé"
    return 1
  fi
  
  print_info "Détails du groupe '$GROUP_ID' :"
  docker exec -it $KAFKA_CONTAINER kafka-consumer-groups \
    --bootstrap-server $KAFKA_BOOTSTRAP \
    --group $GROUP_ID \
    --describe
}

cmd_reset_offsets() {
  print_header "Réinitialiser les offsets du groupe"
  
  print_warning "Attention ! Cette action va recommencer depuis le début du topic"
  read -p "Êtes-vous sûr ? (y/N) " -n 1 -r
  echo
  
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_info "Annulé"
    return 0
  fi
  
  if ! docker inspect $KAFKA_CONTAINER > /dev/null 2>&1; then
    print_error "Conteneur Kafka '$KAFKA_CONTAINER' non trouvé"
    return 1
  fi
  
  print_info "Réinitialisation des offsets ..."
  docker exec -it $KAFKA_CONTAINER kafka-consumer-groups \
    --bootstrap-server $KAFKA_BOOTSTRAP \
    --group $GROUP_ID \
    --topic $TOPIC \
    --reset-offsets \
    --to-earliest \
    --execute
  
  print_success "Offsets réinitialisés à 'earliest'"
}

cmd_health() {
  print_header "Vérifier la santé de l'instance"
  
  if ! curl -s "$QUARKUS_URL/api/partitions/health" > /dev/null; then
    print_error "Quarkus est-il démarré sur $QUARKUS_URL ?"
    return 1
  fi
  
  HEALTH=$(curl -s "$QUARKUS_URL/api/partitions/health")
  
  echo "$HEALTH" | jq .
  
  READY=$(echo "$HEALTH" | jq -r '.ready')
  if [ "$READY" = "true" ]; then
    print_success "Instance HEALTHY 🟢"
  else
    print_warning "Instance en REBALANCING 🟡"
  fi
}

cmd_help() {
  echo "🧪 Script de test pour Consumer Groupe avec Partitions"
  echo ""
  echo "Usage: $0 [command]"
  echo ""
  echo "Commands:"
  echo "  status       Vérifier l'état des partitions (API)"
  echo "  send-one     Envoyer une seule commande de test"
  echo "  send-bulk    Envoyer 100 commandes"
  echo "  kafka-list   Lister les consumer groups (CLI Kafka)"
  echo "  kafka-desc   Décrire le consumer group"
  echo "  reset        Réinitialiser les offsets ⚠️"
  echo "  health       Vérifier la santé de l'instance"
  echo "  help         Afficher cette aide"
  echo ""
  echo "Example:"
  echo "  $0 status        # Vérifier l'état"
  echo "  $0 send-one      # Envoyer un test"
  echo "  $0 kafka-desc    # Voir les détails Kafka"
}

# ──────────────────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────────────────

COMMAND="${1:-help}"

case "$COMMAND" in
  status)
    cmd_status
    ;;
  send-one)
    cmd_send_one
    ;;
  send-bulk)
    cmd_send_bulk
    ;;
  kafka-list)
    cmd_kafka_list
    ;;
  kafka-desc)
    cmd_kafka_describe
    ;;
  reset)
    cmd_reset_offsets
    ;;
  health)
    cmd_health
    ;;
  help)
    cmd_help
    ;;
  *)
    print_error "Commande inconnue: $COMMAND"
    cmd_help
    exit 1
    ;;
esac
