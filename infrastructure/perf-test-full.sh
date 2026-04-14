#!/bin/bash

# ==============================================================================
# Kafka Full Performance Test - Test Massif Complet
# ==============================================================================
# Ce script orchestre un test massif complet:
# 1. Lance des producteurs en parallèle
# 2. Lance des consommateurs concurrents
# 3. Collecte les métriques et les affiche
#
# Usage: ./perf-test-full.sh [topic] [num_messages] [message_size] [num_producers] [num_consumers]
# Exemple: ./perf-test-full.sh orders-events 1000000 1024 3 2
# ==============================================================================

set -e

# Couleurs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Paramètres par défaut
TOPIC="${1:-orders-events}"
NUM_MESSAGES="${2:-500000}"
MESSAGE_SIZE="${3:-1024}"
NUM_PRODUCERS="${4:-3}"
NUM_CONSUMERS="${5:-2}"
# IMPORTANT: Utiliser les noms DNS internes du réseau Docker!
BOOTSTRAP_SERVERS="kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092"

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

print_stage() {
  echo -e "${CYAN}▶ $1${NC}"
}

# Header
print_header "🚀 KAFKA FULL PERFORMANCE TEST - TEST MASSIF COMPLET"

print_stage "Configuration"
print_info "Topic: $TOPIC"
print_info "Nombre de messages par producteur: $NUM_MESSAGES"
print_info "Taille des messages: ${MESSAGE_SIZE} bytes"
print_info "Nombre de producteurs parallèles: $NUM_PRODUCERS"
print_info "Nombre de consommateurs: $NUM_CONSUMERS"
print_info "Bootstrap servers: $BOOTSTRAP_SERVERS"
echo ""

# Script du répertoire courant
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Vérifier que les scripts perf-test existent
if [ ! -f "$SCRIPT_DIR/perf-test-producer.sh" ] || [ ! -f "$SCRIPT_DIR/perf-test-consumer.sh" ]; then
  print_warning "Les scripts perf-test-producer.sh et perf-test-consumer.sh doivent être dans le même répertoire"
  exit 1
fi

# Rendre les scripts exécutables
chmod +x "$SCRIPT_DIR/perf-test-producer.sh"
chmod +x "$SCRIPT_DIR/perf-test-consumer.sh"

print_header "📊 PHASE 1: LANCEMENT DES PRODUCTEURS"

# Lancer les producteurs en parallèle
PRODUCER_PIDS=()
for i in $(seq 1 $NUM_PRODUCERS); do
  print_stage "Producteur $i/$NUM_PRODUCERS"
  (
    echo "Producteur $i en cours d'exécution..."
    "$SCRIPT_DIR/perf-test-producer.sh" "$TOPIC" "$NUM_MESSAGES" "$MESSAGE_SIZE"
    echo "Producteur $i terminé"
  ) &
  PRODUCER_PIDS+=($!)
  sleep 1  # Décalage léger pour éviter les spikes simultanés
done

print_info "Attente que tous les producteurs terminent..."
for i in "${PRODUCER_PIDS[@]}"; do
  wait $i
done
print_success "Tous les producteurs ont terminé"
echo ""

# Attendre un peu avant de lancer les consommateurs
print_info "Pause de 5 secondes avant de lancer les consommateurs..."
sleep 5

print_header "📊 PHASE 2: LANCEMENT DES CONSOMMATEURS"

# Calculer le nombre total de messages produits
TOTAL_MESSAGES=$((NUM_MESSAGES * NUM_PRODUCERS))

# Lancer les consommateurs
CONSUMER_PIDS=()
for i in $(seq 1 $NUM_CONSUMERS); do
  print_stage "Consommateur $i/$NUM_CONSUMERS"
  (
    echo "Consommateur $i en cours d'exécution..."
    "$SCRIPT_DIR/perf-test-consumer.sh" "$TOPIC" "$TOTAL_MESSAGES"
    echo "Consommateur $i terminé"
  ) &
  CONSUMER_PIDS+=($!)
  sleep 1
done

print_info "Attente que tous les consommateurs terminent..."
for i in "${CONSUMER_PIDS[@]}"; do
  wait $i
done
print_success "Tous les consommateurs ont terminé"
echo ""

print_header "📈 RÉSUMÉ DU TEST"
print_success "Test massif complet terminé avec succès!"
print_info "Statistiques:"
print_info "  Total messages produits: $TOTAL_MESSAGES"
print_info "  Taille totale: ~$((TOTAL_MESSAGES * MESSAGE_SIZE / 1024 / 1024))MB"
print_info "  Nombre de producteurs: $NUM_PRODUCERS"
print_info "  Nombre de consommateurs: $NUM_CONSUMERS"
print_info "  Topic: $TOPIC"
echo ""
print_info "📊 Consultez les métriques dans Grafana: http://localhost:3000"
print_info "📊 User/Password: admin/insset"
