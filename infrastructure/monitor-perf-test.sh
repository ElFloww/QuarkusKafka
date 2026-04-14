#!/bin/bash

# ==============================================================================
# Real-time Kafka & Docker Statistics Monitor
# ==============================================================================
# Ce script affiche les statistiques des brokers et du système en temps réel
# pendant que les tests de performance s'exécutent
#
# Usage: ./monitor-perf-test.sh [refresh_interval]
# Exemple: ./monitor-perf-test.sh 2  # Rafraîchissement tous les 2 secondes
# ==============================================================================

set -e

# Couleurs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

REFRESH_INTERVAL="${1:-2}"  # Intervalle de rafraîchissement en secondes

print_header() {
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BLUE}$1${NC}"
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_info() {
  echo -e "${BLUE}ℹ️  $1${NC}"
}

# Fonction pour afficher les stats en temps réel
monitor_loop() {
  while true; do
    clear
    print_header "📊 KAFKA PERFORMANCE MONITOR EN TEMPS RÉEL"
    
    echo ""
    echo -e "${CYAN}Timestamp: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
    echo ""
    
    # Section 1: État des containers
    print_header "🐳 ÉTAT DES BROKERS KAFKA"
    
    docker ps --filter "name=tuuuur-kafka" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || {
      echo -e "${RED}Erreur: Impossible de récupérer l'état des containers${NC}"
    }
    
    echo ""
    
    # Section 2: Utilisation des ressources
    print_header "💻 UTILISATION DES RESSOURCES"
    
    docker stats --no-stream tuuuur-kafka-1 tuuuur-kafka-2 tuuuur-kafka-3 2>/dev/null | awk '
    BEGIN { 
        printf "%-20s %-12s %-12s %-12s %-12s\n", "CONTAINER", "CPU %", "MEM USAGE", "MEM %", "NET I/O"
        printf "%-20s %-12s %-12s %-12s %-12s\n", "────────────────────", "────────────", "────────────", "────────────", "────────────"
    }
    NR > 1 {
        printf "%-20s %-12s %-12s %-12s %-12s\n", $1, $2, $3 " / " $4, $5, $8
    }
    ' || echo -e "${YELLOW}Containers non disponibles${NC}"
    
    echo ""
    
    # Section 3: Information des topics
    print_header "📚 TOPICS KAFKA"
    
    if docker exec tuuuur-kafka-1 kafka-topics --bootstrap-server localhost:9092 --describe 2>/dev/null | head -20; then
      echo ""
      echo -e "${YELLOW}... (affichage limité à 20 lignes)${NC}"
    else
      echo -e "${RED}Impossible de récupérer les informations des topics${NC}"
    fi
    
    echo ""
    
    # Section 4: Informations du système
    print_header "🖥️  SYSTÈME"
    
    if [[ "$OSTYPE" == "darwin"* ]]; then
      # macOS
      echo "OS: macOS"
      echo "CPU $(sysctl -n hw.ncpu) cores"
      echo "Mémoire: $(vm_stat | grep "Pages active:" | awk '{print $3}') active pages"
      echo "Disk: $(df -h / | tail -1 | awk '{print $5}' FS=' +')"
    else
      # Linux
      echo "OS: Linux"
      echo "CPU: $(nproc) cores"
      echo "Mémoire: $(free -h | grep Mem | awk '{print $3 "/" $2}')"
      echo "Disk: $(df -h / | tail -1 | awk '{print $5}')"
    fi
    
    echo ""
    echo -e "${CYAN}▶ Prochain rafraîchissement dans ${REFRESH_INTERVAL}s...${NC}"
    echo -e "${CYAN}Appuyez sur Ctrl+C pour arrêter${NC}"
    echo ""
    
    sleep "$REFRESH_INTERVAL"
  done
}

# Gestion du signal d'interruption
trap 'echo ""; print_header "✅ MONITORING ARRÊTÉ"; exit 0' SIGINT SIGTERM

print_info "Démarrage du monitoring en temps réel..."
print_info "Intervalle de rafraîchissement: ${REFRESH_INTERVAL}s"
sleep 1

monitor_loop
