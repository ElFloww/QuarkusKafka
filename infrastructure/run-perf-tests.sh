#!/bin/bash

# ==============================================================================
# Kafka Performance Test Runner - Orchestrateur de tests
# ==============================================================================
# Ce script interactif permet de sélectionner et lancer différents profils de test
#
# Usage: ./run-perf-tests.sh
# ==============================================================================

set -e

# Couleurs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

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

# Script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Fonctions pour les profils de test
run_debug_test() {
  print_header "🐛 TEST LÉGER (DEBUG)"
  print_info "Configuration: 10k messages, 512 bytes, test simple"
  ./perf-test-producer.sh orders-events 10000 512
  ./perf-test-consumer.sh orders-events 10000
}

run_standard_test() {
  print_header "⚙️  TEST MODÉRÉ (STANDARD)"
  print_info "Configuration: 2 producteurs, 2 consommateurs, 100k messages"
  ./perf-test-full.sh orders-events 100000 1024 2 2
}

run_heavy_test() {
  print_header "💪 TEST LOURD (STRESS)"
  print_info "Configuration: 3 producteurs, 3 consommateurs, 500k messages"
  print_warning "Cela prendra 5-15 minutes"
  ./perf-test-full.sh orders-events 500000 2048 3 3
}

run_extreme_test() {
  print_header "🔥 TEST EXTRÊME (LIMITE)"
  print_info "Configuration: 5 producteurs, 5 consommateurs, 1M messages"
  print_warning "⚠️  ATTENTION: Cela peut surcharger votre système!"
  print_warning "Temps estimé: 20-30 minutes"
  read -p "Êtes-vous sûr? (y/n) " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    ./perf-test-full.sh orders-events 10000000 128 5 5
  else
    print_info "Annulé"
  fi
}

run_custom_test() {
  print_header "🎨 TEST PERSONNALISÉ"
  
  read -p "Topic [orders-events]: " topic
  topic="${topic:-orders-events}"
  
  read -p "Nombre de messages par producteur [500000]: " num_messages
  num_messages="${num_messages:-500000}"
  
  read -p "Taille des messages en bytes [1024]: " message_size
  message_size="${message_size:-1024}"
  
  read -p "Nombre de producteurs [3]: " num_producers
  num_producers="${num_producers:-3}"
  
  read -p "Nombre de consommateurs [2]: " num_consumers
  num_consumers="${num_consumers:-2}"
  
  print_header "🎨 LANCEMENT DU TEST PERSONNALISÉ"
  print_info "Topic: $topic"
  print_info "Messages/producteur: $num_messages"
  print_info "Taille: $message_size bytes"
  print_info "Producteurs: $num_producers"
  print_info "Consommateurs: $num_consumers"
  
  ./perf-test-full.sh "$topic" "$num_messages" "$message_size" "$num_producers" "$num_consumers"
}

run_producer_only() {
  print_header "📤 TEST PRODUCTEUR SEUL"
  
  read -p "Topic [orders-events]: " topic
  topic="${topic:-orders-events}"
  
  read -p "Nombre de messages [500000]: " num_messages
  num_messages="${num_messages:-500000}"
  
  read -p "Taille des messages [1024]: " message_size
  message_size="${message_size:-1024}"
  
  ./perf-test-producer.sh "$topic" "$num_messages" "$message_size"
}

run_consumer_only() {
  print_header "📥 TEST CONSOMMATEUR SEUL"
  
  read -p "Topic [orders-events]: " topic
  topic="${topic:-orders-events}"
  
  read -p "Nombre de messages [500000]: " num_messages
  num_messages="${num_messages:-500000}"
  
  ./perf-test-consumer.sh "$topic" "$num_messages"
}

check_cluster_health() {
  print_header "🏥 VÉRIFICATION DE LA SANTÉ DU CLUSTER"
  
  print_stage "Vérification des brokers..."
  
  for i in 1 2 3; do
    PORT=$((9091 + i))
    CONTAINER="tuuuur-kafka-$i"
    
    if docker ps | grep -q "$CONTAINER"; then
      # Broker is running
      if docker exec "$CONTAINER" kafka-broker-api-versions --bootstrap-server localhost:9092 &> /dev/null; then
        print_success "Broker $i (port $PORT): ✅ ACTIF"
      else
        print_warning "Broker $i (port $PORT): ⚠️  EXISTE MAIS NE RÉPOND PAS"
      fi
    else
      print_warning "Broker $i: ❌ ARRÊTÉ"
    fi
  done
  
  echo ""
  print_stage "Vérification des topics..."
  
  TOPICS=$(docker exec tuuuur-kafka-1 kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null || echo "")
  if [ -z "$TOPICS" ]; then
    print_warning "Aucun topic trouvé"
  else
    echo -e "${BLUE}Topics disponibles:${NC}"
    echo "$TOPICS" | while read topic; do
      echo "  - $topic"
    done
  fi
}

show_stats() {
  print_header "📊 STATISTIQUES DES BROKERS"
  
  docker stats --no-stream tuuuur-kafka-1 tuuuur-kafka-2 tuuuur-kafka-3 2>/dev/null || {
    print_warning "Could not retrieve stats. Make sure containers are running."
  }
}

# Menu principal
show_menu() {
  clear
  print_header "🚀 KAFKA PERFORMANCE TEST RUNNER"
  
  echo ""
  echo "Profils de test disponibles:"
  echo ""
  echo -e "${CYAN}1)${NC} 🐛 Test léger (Debug)          - 10k messages, ~10s"
  echo -e "${CYAN}2)${NC} ⚙️  Test modéré (Standard)      - 200k messages, ~2-3min"
  echo -e "${CYAN}3)${NC} 💪 Test lourd (Stress)         - 1.5M messages, ~5-10min"
  echo -e "${CYAN}4)${NC} 🔥 Test extrême (Limite)       - 5M messages, ~20min ⚠️"
  echo -e "${CYAN}5)${NC} 🎨 Test personnalisé"
  echo ""
  echo "Tests détaillés:"
  echo -e "${CYAN}6)${NC} 📤 Producteur seul"
  echo -e "${CYAN}7)${NC} 📥 Consommateur seul"
  echo ""
  echo "Utilitaires:"
  echo -e "${CYAN}8)${NC} 🏥 Vérifier la santé du cluster"
  echo -e "${CYAN}9)${NC} 📊 Afficher les statistiques des brokers"
  echo ""
  echo -e "${CYAN}0)${NC} ❌ Quitter"
  echo ""
  read -p "Sélectionnez une option: " choice
}

# Boucle principale
while true; do
  show_menu
  
  case $choice in
    1)
      cd "$SCRIPT_DIR"
      run_debug_test
      ;;
    2)
      cd "$SCRIPT_DIR"
      run_standard_test
      ;;
    3)
      cd "$SCRIPT_DIR"
      run_heavy_test
      ;;
    4)
      cd "$SCRIPT_DIR"
      run_extreme_test
      ;;
    5)
      cd "$SCRIPT_DIR"
      run_custom_test
      ;;
    6)
      cd "$SCRIPT_DIR"
      run_producer_only
      ;;
    7)
      cd "$SCRIPT_DIR"
      run_consumer_only
      ;;
    8)
      check_cluster_health
      ;;
    9)
      show_stats
      ;;
    0)
      print_info "Au revoir!"
      exit 0
      ;;
    *)
      print_warning "Option invalide"
      ;;
  esac
  
  echo ""
  read -p "Appuyez sur Entrée pour continuer..."
done
