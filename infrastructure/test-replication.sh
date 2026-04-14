#!/bin/bash

# ==============================================================================
# Test Script - Kafka Cluster Replication
# ==============================================================================
# Ce script permet de tester la réplication sur le cluster de 3 brokers

set -e

BOOTSTRAP_SERVERS="kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092"
DOCKER_IMAGE="confluentinc/cp-kafka:7.6.0"

echo "╔══════════════════════════════════════════════════════════════════════════╗"
echo "║           KAFKA CLUSTER REPLICATION TEST - 3 BROKERS                    ║"
echo "╚══════════════════════════════════════════════════════════════════════════╝"
echo ""

# Fonction pour exécuter une commande dans un container
run_kafka_cmd() {
    docker run --rm --network tuuuur-net "$DOCKER_IMAGE" "$@"
}

# 1. Vérifier l'état du cluster
echo "📋 1. État du cluster Kafka"
echo "────────────────────────────────────────────────────────────────────────────"
run_kafka_cmd kafka-broker-api-versions --bootstrap-server "$BOOTSTRAP_SERVERS" | head -10
echo ""

# 2. Lister les brokers du cluster
echo "📋 2. Brokers du cluster"
echo "────────────────────────────────────────────────────────────────────────────"
run_kafka_cmd kafka-metadata-quorum --bootstrap-server "$BOOTSTRAP_SERVERS" describe --status || echo "Note: KRaft metadata peut ne pas être directement queryable"
echo ""

# 3. Lister les topics
echo "📋 3. Topics"
echo "────────────────────────────────────────────────────────────────────────────"
run_kafka_cmd kafka-topics --bootstrap-server "$BOOTSTRAP_SERVERS" --list
echo ""

# 4. Décrire les détails des topics (réplication)
echo "📋 4. Détails des topics (ISR - In-Sync Replicas)"
echo "────────────────────────────────────────────────────────────────────────────"
run_kafka_cmd kafka-topics --bootstrap-server "$BOOTSTRAP_SERVERS" --describe
echo ""

# 5. Vérifier les consumer groups
echo "📋 5. Consumer Groups"
echo "────────────────────────────────────────────────────────────────────────────"
run_kafka_cmd kafka-consumer-groups --bootstrap-server "$BOOTSTRAP_SERVERS" --list
echo ""

# 6. Compter les messages par topic
echo "📋 6. Nombre de messages par topic"
echo "────────────────────────────────────────────────────────────────────────────"
for topic in orders-events rank-events stock-events payment-events analytics-stats; do
    count=$(run_kafka_cmd kafka-run-class kafka.tools.EndToEndLatency "$BOOTSTRAP_SERVERS" "$topic" 100 2>/dev/null | tail -1 || echo "N/A")
    echo "  $topic: $count"
done
echo ""

# 7. Test de failover - Instructions
echo "📋 7. Test de failover (résilience)"
echo "────────────────────────────────────────────────────────────────────────────"
echo "Pour tester la résilience du cluster:"
echo ""
echo "  1. Arrêtez un broker (par ex., broker-2):"
echo "     docker-compose stop kafka-broker-2"
echo ""
echo "  2. Vérifiez que le cluster reste opérationnel:"
echo "     docker run --rm --network tuuuur-net confluentinc/cp-kafka:7.6.0 \\"
echo "       kafka-topics --bootstrap-server kafka-broker-1:29092 --describe"
echo ""
echo "  3. Observez les ISR qui diminuent (les replicas in-sync)"
echo ""
echo "  4. Relancez le broker:"
echo "     docker-compose up -d kafka-broker-2"
echo ""
echo "  5. Vérifiez que le broker se rejoint et que les ISR se restaurent"
echo ""

# 8. Générateur de messages - Instructions
echo "📋 8. Générer des messages de test"
echo "────────────────────────────────────────────────────────────────────────────"
echo "Producteur à partir du host:"
echo "  docker run --rm --network tuuuur-net confluentinc/cp-kafka:7.6.0 \\"
echo "    kafka-console-producer --broker-list kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092 \\"
echo "    --topic orders-events"
echo ""
echo "Consommateur à partir du host:"
echo "  docker run --rm --network tuuuur-net confluentinc/cp-kafka:7.6.0 \\"
echo "    kafka-console-consumer --bootstrap-server kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092 \\"
echo "    --topic orders-events --from-beginning"
echo ""

# 9. Interfaces de monitoring
echo "📋 9. Interfaces de monitoring disponibles"
echo "────────────────────────────────────────────────────────────────────────────"
echo "  🌐 Kafka UI:      http://localhost:8090"
echo "  📊 Prometheus:    http://localhost:9090"
echo "  📈 Grafana:       http://localhost:3000 (login: admin / tuuuur2025)"
echo ""

echo "✅ Test complet! Utilisez les commandes ci-dessus pour explorer le cluster."
