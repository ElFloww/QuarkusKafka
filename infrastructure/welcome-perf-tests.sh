#!/bin/bash

# ==============================================================================
# Welcome to Kafka Performance Testing Suite
# ==============================================================================

# Couleurs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

clear

# Banner
echo -e "${MAGENTA}"
cat << "EOF"
╔══════════════════════════════════════════════════════════════════════════════╗
║                                                                              ║
║   🚀 KAFKA PERFORMANCE TESTING SUITE - Tests Massifs 🚀                    ║
║                                                                              ║
║   Teste les performances de vos brokers Kafka avec des volumes massifs!     ║
║                                                                              ║
╚══════════════════════════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}"

echo ""
echo -e "${BLUE}📊 STATISTIQUES DU CLUSTER${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Vérifier l'état des brokers
echo ""
BROKERS_OK=0
for i in 1 2 3; do
  PORT=$((9091 + i))
  CONTAINER="tuuuur-kafka-$i"
  
  if docker ps --format "{{.Names}}" | grep -q "^${CONTAINER}$"; then
    if docker exec "$CONTAINER" kafka-broker-api-versions --bootstrap-server localhost:9092 &> /dev/null; then
      echo -e "${GREEN}✅ Broker $i${NC} (port $PORT): ACTIF"
      ((BROKERS_OK++))
    else
      echo -e "${YELLOW}⚠️  Broker $i${NC} (port $PORT): N'ATURE PAS"
    fi
  else
    echo -e "${RED}❌ Broker $i${NC}: ARRÊTÉ"
  fi
done

echo ""
if [ $BROKERS_OK -eq 3 ]; then
  echo -e "${GREEN}✅ Cluster SAIN et PRÊT (3/3 brokers actifs)${NC}"
elif [ $BROKERS_OK -gt 0 ]; then
  echo -e "${YELLOW}⚠️  Cluster DÉGRADÉ ($BROKERS_OK/3 brokers actifs)${NC}"
else
  echo -e "${RED}❌ Cluster INACTIF - Lancez: docker compose up -d${NC}"
fi

echo ""
echo -e "${BLUE}📚 DOCUMENTATION${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${CYAN}📖 Lisez en premier:${NC}"
echo "   → QUICKSTART_PERF_TESTS.md"
echo ""
echo -e "${CYAN}📚 Documentation complète:${NC}"
echo "   → PERF_TESTING_GUIDE.md (guide détaillé)"
echo "   → SUMMARY_PERF_TESTS.md (résumé complet)"
echo ""

echo -e "${BLUE}🚀 COMMANDES RAPIDES${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${CYAN}1. Menu interactif (RECOMMANDÉ):${NC}"
echo "   $ ./run-perf-tests.sh"
echo ""
echo -e "${CYAN}2. Test simple (10 sec):${NC}"
echo "   $ ./perf-test-producer.sh orders-events 10000 512"
echo ""
echo -e "${CYAN}3. Test complet (5-15 min):${NC}"
echo "   $ ./perf-test-full.sh orders-events 500000 2048 3 3"
echo ""
echo -e "${CYAN}4. Monitoring en direct:${NC}"
echo "   $ ./monitor-perf-test.sh 2"
echo ""

echo -e "${BLUE}📈 PROFILS DE TEST${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
printf "%-25s %-15s %-15s %-10s\n" "PROFIL" "MESSAGES" "DURÉE" "USAGE"
printf "%-25s %-15s %-15s %-10s\n" "───────────────────────" "───────────" "───────────" "─────────"
printf "%-25s %-15s %-15s %-10s\n" "🐛 Léger (Debug)" "10k" "~10s" "Validate"
printf "%-25s %-15s %-15s %-10s\n" "⚙️  Modéré (Standard)" "200k" "2-3 min" "Normal"
printf "%-25s %-15s %-15s %-10s\n" "💪 Lourd (Stress)" "1.5M" "5-15 min" "Stress"
printf "%-25s %-15s %-15s %-10s\n" "🔥 Extrême (Limite)" "5M" "20-30 min" "Limite"
echo ""

echo -e "${BLUE}📊 MÉTRIQUES À OBSERVER${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "  📤 Throughput (msg/sec): Cible > 50,000"
echo "  📥 Latency (average):     Cible < 50ms"
echo "  ⏱️  Latency (max):          Cible < 500ms"
echo "  💾 CPU Usage:             Cible < 80%"
echo "  🖥️  Memory Usage:           Cible < 70%"
echo ""

echo -e "${BLUE}🎯 ÉTAPES SUIVANTES${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "1️⃣  Lisez QUICKSTART_PERF_TESTS.md"
echo "2️⃣  Lancez: ./run-perf-tests.sh"
echo "3️⃣  Choisissez un profil (Option 1, 2, 3, 4)"
echo "4️⃣  Consultez les résultats dans Grafana"
echo "   → http://localhost:3000 (admin/insset)"
echo ""

echo -e "${GREEN}═══════════════════════════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}Vous êtes prêt! Lancez:${NC}"
echo -e "${CYAN}cd infrastructure && ./run-perf-tests.sh${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════════════════════════════════${NC}"
echo ""
