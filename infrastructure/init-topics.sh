#!/bin/bash

# ==============================================================================
# init-topics.sh – Initialize Kafka Topics
# ==============================================================================
# This script creates all required Kafka topics for the Tuuuur Merch Saga.
# It uses kafka-topics.sh which is available inside the Kafka container.
#
# Topics created:
#   - orders-events (3 partitions)
#   - rank-events (3 partitions)
#   - stock-events (3 partitions)
#   - payment-events (3 partitions)
#   - analytics-stats (1 partition)
#
# Usage: ./infrastructure/init-topics.sh
# ==============================================================================

set -e

KAFKA_CONTAINER="tuuuur-kafka"
BOOTSTRAP_SERVER="kafka:29092"  # Internal container address

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BLUE}$1${NC}"
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
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

# ==============================================================================
# Check if Kafka container is running
# ==============================================================================

print_header "Checking Kafka Container"

if ! docker ps | grep -q "$KAFKA_CONTAINER"; then
  print_error "Kafka container '$KAFKA_CONTAINER' is not running!"
  print_info "Please run: docker compose -f infrastructure/docker-compose.yml up -d"
  exit 1
fi

print_success "Kafka container is running"

# ==============================================================================
# Define topics (matching KafkaTopicInitializer.java)
# ==============================================================================

declare -a TOPICS=(
  "orders-events:3:1"      # topic_name:partitions:replication_factor
  "rank-events:3:1"
  "stock-events:3:1"
  "payment-events:3:1"
  "analytics-stats:1:1"
)

# ==============================================================================
# Create Topics
# ==============================================================================

print_header "Creating Kafka Topics"

for topic_spec in "${TOPICS[@]}"; do
  IFS=':' read -r topic_name partitions replication_factor <<< "$topic_spec"
  
  print_info "Creating topic: $topic_name ($partitions partitions, replication factor: $replication_factor)"
  
  # Create topic inside the container
  if docker exec "$KAFKA_CONTAINER" kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --topic "$topic_name" \
    --partitions "$partitions" \
    --replication-factor "$replication_factor" \
    --if-not-exists 2>/dev/null; then
    print_success "Topic '$topic_name' created or already exists"
  else
    # Check if it already exists
    if docker exec "$KAFKA_CONTAINER" kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" \
      --list 2>/dev/null | grep -q "^${topic_name}$"; then
      print_success "Topic '$topic_name' already exists"
    else
      print_error "Failed to create topic '$topic_name'"
      exit 1
    fi
  fi
done

# ==============================================================================
# List All Topics
# ==============================================================================

print_header "Listing All Topics"

echo ""
docker exec "$KAFKA_CONTAINER" kafka-topics --bootstrap-server "$BOOTSTRAP_SERVER" --list
echo ""

print_success "Topic initialization complete!"
print_info "Your Quarkus application can now connect to Kafka and use these topics."
