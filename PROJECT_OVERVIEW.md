# 🎮 Tuuuur Merch Shop – Project Overview

## 📌 Project Purpose

**Tuuuur Merch Shop** is a **full event-driven demonstration application** built with **Quarkus + Kafka** that simulates an e-commerce platform for gaming merchandise. The project showcases modern microservices patterns including:

- **Saga Pattern** (Choreography-based) for distributed transaction management
- **Event Sourcing** with Kafka topics
- **Kafka Streams** for real-time analytics
- **Reactive Messaging** using SmallRye
- **Consumer Group Partitioning** and Rebalancing
- **Real-time Monitoring** with Grafana + Prometheus

---

## 🏗️ Architecture Overview

### Two Quarkus Applications

```
┌─────────────────────────────────────────────────────────────────┐
│ MAIN APP (port 8080)                                            │
├─────────────────────────────────────────────────────────────────┤
│ - Order Service (produces orders-events)                        │
│ - GameSync Service (validates player ranks)                     │
│ - Stock Service (checks & reserves inventory)                   │
│ - Order Confirmation Handler                                    │
│ - Analytics Real-time Dashboard (Kafka Streams)                 │
│ - Shop UI (HTML + Qute templates)                               │
└─────────────────────────────────────────────────────────────────┘
              ↕ Kafka Topics (orders-events, rank-events, etc.)
┌─────────────────────────────────────────────────────────────────┐
│ CONSUMER APP (port 8081)                                        │
├─────────────────────────────────────────────────────────────────┤
│ - GameSync Service (consumer group duplicate)                   │
│ - Payment Service (mock payment processing)                     │
│ - Order Confirmation Consumer                                   │
│ - Partitioned Consumer (monitoring rebalancing)                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📊 Main Components & Services

### 1. **Order Service** (Main App)

**Location**: `src/main/java/upjv/insset/api/order/`

**Files**:

- `OrderResource.java` - REST API endpoints
- `OrderService.java` - Producer API for Kafka
- `Order.java` - Order entity model
- `OrderStatus.java` - Order lifecycle enum
- `OrderRepository.java` - In-memory order storage

**Responsibilities**:

- Exposes REST API to create/list orders
- Validates player rank and item availability
- Creates `OrderCreatedEvent` and publishes to Kafka
- Tracks all orders in memory (ConcurrentHashMap)
- Updates order status as saga progresses

**Metrics**:

- `tuuuur.orders.created` - Total orders created
- `tuuuur.orders.confirmed` - Total orders confirmed

---

### 2. **GameSync Service** (Consumer in both apps)

**Location**:

- Main: `src/main/java/upjv/insset/kafka/consumers/GameSyncService.java`
- Consumer: `consumer/src/main/java/upjv/insset/kafka/consumers/GameSyncService.java`

**Role in Saga**: **Step 2 - Rank Validation**

**Responsibilities**:

- Consumes `OrderCreatedEvent` from `orders-events` topic
- Validates that player's current rank ≥ required rank for item
- Produces `RankVerifiedEvent` to `rank-events` topic (always, even if validation fails)
- Enables proper saga termination with rejection status

**Concepts Demonstrated**:

- Consumer + Producer in same bean
- Conditional processing with status propagation
- Key preservation for message ordering

**Metrics**:

- `tuuuur.gamesync.rank.ok` - Valid rank confirmations
- `tuuuur.gamesync.rank.ko` - Insufficient rank rejections

---

### 3. **Stock Service** (Main & Consumer App)

**Location**:

- Main: `src/main/java/upjv/insset/api/stock/`
- Consumer: `consumer/src/main/java/upjv/insset/kafka/consumers/StockService.java`

**Role in Saga**: **Step 3 - Stock Reservation**

**Responsibilities**:

- Consumes `RankVerifiedEvent` from `rank-events` topic
- Checks virtual stock (starts at 50 units per item)
- Decrements inventory atomically using `AtomicInteger`
- Produces `StockReservedEvent` to `stock-events` topic
- Aborts saga if:
  - Rank check failed (from `RankVerifiedEvent.rankOk = false`)
  - Stock is insufficient

**Stock Implementation**:

- In-memory `ConcurrentHashMap<itemId, AtomicInteger>`
- Thread-safe atomic operations
- Mock inventory (not persisted to DB)

**Metrics**:

- `tuuuur.stock.reserved` - Successfully reserved items
- `tuuuur.stock.available` - Gauge for current inventory levels

---

### 4. **Payment Service** (Consumer App)

**Location**: `consumer/src/main/java/upjv/insset/kafka/consumers/PaymentService.java`

**Role in Saga**: **Step 4 - Mock Payment Processing**

**Responsibilities**:

- Consumes `StockReservedEvent` from `stock-events` topic
- Simulates payment processing (`Thread.sleep(1000ms)`)
- **Always succeeds** in this demo (no failure scenarios)
- Produces `PaymentSucceededEvent` to `payment-events` topic
- Calculates total amount (quantity × unitPrice)
- Generates mock transaction ID

**Concepts Demonstrated**:

- Blocking consumer with @Blocking annotation
- Message acknowledgment (ack/nack)
- Timer metrics for processing duration

**Metrics**:

- `tuuuur.payment.processed` - Total payments simulated
- `tuuuur.payment.duration` - Processing time (p50, p95, p99)

---

### 5. **Order Confirmation Handlers**

**Location**:

- Main: `src/main/java/upjv/insset/kafka/consumers/OrderConfirmationConsumer.java`
- Consumer: `consumer/src/main/java/upjv/insset/kafka/consumers/OrderConfirmationConsumer.java`

**Role in Saga**: **Step 5 - Final Order Status Update**

**Responsibilities**:

- Consumes `PaymentSucceededEvent` from `payment-events` topic
- Updates order status from `PENDING` → `CONFIRMED`
- Also observes intermediate events:
  - `RankVerifiedEvent` → status = `RANK_VERIFIED` or `RANK_REJECTED`
  - `StockReservedEvent` → status = `STOCK_RESERVED` or `STOCK_FAILED`
- Maintains order state visible on shop UI

---

### 6. **Shop UI Service**

**Location**: `src/main/java/upjv/insset/api/shop/ShopResource.java`

**Responsibilities**:

- Serves HTML template for merchandise shop
- Displays product catalog with price and required rank
- Shows real-time order tracking table
- Provides forms to place new orders

**Template**: `src/main/resources/templates/shop.html`

---

### 7. **Analytics Service** (Real-time via Kafka Streams)

**Location**:

- Topology: `src/main/java/upjv/insset/kafka/topology/SalesAnalyticsTopology.java`
- Query: `src/main/java/upjv/insset/kafka/topology/SalesQueryService.java`
- REST API: `src/main/java/upjv/insset/api/analytics/AnalyticsResource.java`

**Role**: **Real-time Sales Analytics**

**How it Works**:

1. Consumes `PaymentSucceededEvent` from `payment-events` topic
2. Builds a KStream pipeline:
   - Filters valid events
   - Re-keys by `itemId`
   - Extracts `quantity` sold
   - Groups and reduces to get cumulative sales per item
   - Materializes into a State Store (`sales-by-item`)
3. Produces aggregated stats to `analytics-stats` topic

**Kafka Streams Concepts Demonstrated**:

- **KStream**: unbounded stream of records
- **KTable**: materialized changelog (aggregated state)
- **State Store**: in-memory RocksDB for IQ (Interactive Queries)
- **selectKey**: repartitioning by item ID
- **reduce**: summation aggregation

**REST Endpoints**:

- `GET /api/analytics/sales` - All sales stats
- `GET /api/analytics/sales/{itemId}` - Sales for specific item
- `GET /api/analytics/streams/state` - Pipeline state (RUNNING, REBALANCING, etc.)

---

### 8. **Partition Monitoring Service**

**Location**: `src/main/java/upjv/insset/kafka/services/PartitionRebalanceListener.java`

**Responsibilities**:

- Implements `KafkaConsumerRebalanceListener`
- Tracks partition assignments and revocations
- Logs rebalancing events for debugging
- Publishes metrics on partition count
- Enables graceful cleanup before partition loss

**Hooks**:

- `onPartitionsRevoked()` - Before losing partitions
- `onPartitionsAssigned()` - After gaining partitions

**REST Monitoring API** (PartitionMonitoringResource):

- `GET /api/partitions/status` - Assigned partitions
- `GET /api/partitions/count` - Number of assigned partitions
- `GET /api/partitions/health` - Consumer health status

---

## 🔄 REST Endpoints Summary

### Order Management

```
POST   /api/orders              Create new order (starts saga)
GET    /api/orders              List all orders
GET    /api/orders/{id}         Get order details
```

### Shop UI

```
GET    /                        Homepage with shop UI
```

### Real-time Analytics

```
GET    /api/analytics/sales                 All sales statistics
GET    /api/analytics/sales/{itemId}        Sales for specific item
GET    /api/analytics/streams/state         Kafka Streams pipeline state
```

### Partition Monitoring

```
GET    /api/partitions/status               Assigned partitions
GET    /api/partitions/count                Number of assigned partitions
GET    /api/partitions/health               Consumer group health
```

---

## 📨 Kafka Topics & Events

### Kafka Topics

| Topic               | Producer               | Consumer                                          | Purpose                         |
| ------------------- | ---------------------- | ------------------------------------------------- | ------------------------------- |
| **orders-events**   | OrderService           | GameSyncService                                   | Orders awaiting rank validation |
| **rank-events**     | GameSyncService        | StockService                                      | Rank verification results       |
| **stock-events**    | StockService           | PaymentService                                    | Stock reservation results       |
| **payment-events**  | PaymentService         | OrderConfirmationConsumer, SalesAnalyticsTopology | Payment completion & analytics  |
| **analytics-stats** | SalesAnalyticsTopology | (Sink topic)                                      | Aggregated sales per item       |

### Event Flow

#### 1. **OrderCreatedEvent** (orders-events)

```json
{
  "orderId": "uuid",
  "playerId": "player-42",
  "playerName": "Kévin_Diamant",
  "playerRank": "DIAMANT",
  "itemId": "tshirt-diamant",
  "itemName": "T-Shirt Rang Diamant",
  "requiredRank": "DIAMANT",
  "quantity": 1,
  "unitPrice": 29.99,
  "createdAt": "2025-05-02T14:30:00Z"
}
```

#### 2. **RankVerifiedEvent** (rank-events)

```json
{
  "orderId": "uuid",
  "playerId": "player-42",
  "playerRank": "DIAMANT",
  "requiredRank": "DIAMANT",
  "rankOk": true, // true = player authorized, false = rejected
  "verifiedAt": "2025-05-02T14:30:01Z"
}
```

#### 3. **StockReservedEvent** (stock-events)

```json
{
  "orderId": "uuid",
  "playerId": "player-42",
  "itemId": "tshirt-diamant",
  "quantity": 1,
  "remainingStock": 49, // After reservation
  "reservedAt": "2025-05-02T14:30:02Z"
}
```

#### 4. **PaymentSucceededEvent** (payment-events)

```json
{
  "orderId": "uuid",
  "playerId": "player-42",
  "itemId": "tshirt-diamant",
  "quantity": 1,
  "totalAmount": 29.99,
  "transactionId": "tx-mock-xxx",
  "paidAt": "2025-05-02T14:30:03Z"
}
```

---

## 🎯 Business Logic & Saga Flow

### Order Saga Choreography Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│ CUSTOMER PLACES ORDER                                           │
│ (POST /api/orders)                                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: Order Created                                           │
│ Status: PENDING                                                 │
│ OrderService publishes OrderCreatedEvent → orders-events       │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 2: Rank Validation                                         │
│ GameSyncService consumes → checks playerRank >= requiredRank   │
├─────────────────────────────────────────────────────────────────┤
│ IF rankOk:                                                      │
│   Status: RANK_VERIFIED                                         │
│   Publishes RankVerifiedEvent (rankOk=true) → rank-events      │
│                                                                 │
│ IF rankKo:                                                      │
│   Status: RANK_REJECTED                                         │
│   Publishes RankVerifiedEvent (rankOk=false) → rank-events     │
│   SAGA ENDS (customer cannot buy this tier item)                │
└─────────────────────────────────────────────────────────────────┘
                              ↓ (only if rankOk=true)
┌─────────────────────────────────────────────────────────────────┐
│ STEP 3: Stock Reservation                                       │
│ StockService consumes RankVerifiedEvent                         │
├─────────────────────────────────────────────────────────────────┤
│ IF rankOk + stock available:                                    │
│   Status: STOCK_RESERVED                                        │
│   Decrements stock: inventory[itemId]--                         │
│   Publishes StockReservedEvent → stock-events                   │
│                                                                 │
│ IF rankKo OR stock empty:                                       │
│   Status: STOCK_FAILED                                          │
│   Publishes nothing (saga stops)                                │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 4: Payment Processing (Mock)                               │
│ PaymentService consumes StockReservedEvent                      │
├─────────────────────────────────────────────────────────────────┤
│ Simulates payment (Thread.sleep 1000ms)                         │
│ Status: (no intermediate status for payment)                    │
│ Publishes PaymentSucceededEvent → payment-events               │
│ (In this demo, payment ALWAYS succeeds)                         │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 5: Order Confirmation (Final)                              │
│ OrderConfirmationConsumer consumes PaymentSucceededEvent        │
├─────────────────────────────────────────────────────────────────┤
│ Status: CONFIRMED                                               │
│ Updates order in OrderRepository                                │
│ SAGA COMPLETES SUCCESSFULLY                                     │
│                                                                 │
│ ALSO: SalesAnalyticsTopology consumes PaymentSucceededEvent     │
│ Aggregates sales stats: itemId → total_quantity_sold           │
└─────────────────────────────────────────────────────────────────┘
```

### Order Status Lifecycle

```
PENDING
  ├─[GameSync KO]─────────→ RANK_REJECTED (END)
  │
  └─[GameSync OK]──────────→ RANK_VERIFIED
                                ├─[Stock KO]──→ STOCK_FAILED (END)
                                │
                                └─[Stock OK]──→ STOCK_RESERVED
                                                 ├─[Payment KO]──→ PAYMENT_FAILED (END)
                                                 │
                                                 └─[Payment OK]──→ CONFIRMED (END ✓)
```

---

## 🛍️ Merchandise Catalog

The shop sells rank-locked merchandise:

| Item ID             | Name                      | Price  | Required Rank |
| ------------------- | ------------------------- | ------ | ------------- |
| `tshirt-bronze`     | T-Shirt Rang Bronze       | €19.99 | BRONZE        |
| `tshirt-argent`     | T-Shirt Rang Argent       | €24.99 | ARGENT        |
| `hoodie-or`         | Hoodie Rang Or            | €49.99 | OR            |
| `casquette-platine` | Casquette Platine Edition | €34.99 | PLATINE       |
| `tshirt-diamant`    | T-Shirt Rang Diamant      | €29.99 | DIAMANT       |
| `hoodie-challenger` | Hoodie Challenger         | €79.99 | CHALLENGER    |

Each item has **50 units** of virtual stock (mock inventory).

---

## 🎮 Player Ranks (Tuuuur Game)

Ranks in ascending order:

1. **BRONZE** - Beginners
2. **ARGENT** - Improving
3. **OR** - Intermediate
4. **PLATINE** - Advanced
5. **DIAMANT** - Expert
6. **CHALLENGER** - Elite

Players can only buy merchandise at their rank or below (no downgrade).

---

## 📊 Key Metrics & Monitoring

### Prometheus Metrics

**Order Metrics**:

- `tuuuur.orders.created` (Counter)
- `tuuuur.orders.confirmed` (Counter)

**GameSync Metrics**:

- `tuuuur.gamesync.rank.ok` (Counter)
- `tuuuur.gamesync.rank.ko` (Counter)

**Stock Metrics**:

- `tuuuur.stock.reserved` (Counter)
- `tuuuur.stock.available` (Gauge per item)

**Payment Metrics**:

- `tuuuur.payment.processed` (Counter)
- `tuuuur.payment.duration` (Timer: p50, p95, p99)

### Grafana Dashboards

Located in: `infrastructure/grafana/provisioning/dashboards/`

- `kafka-dashboard.json` - Kafka broker metrics
- `tuuuur-dashboard.json` - Application-specific metrics

---

## 🐳 Infrastructure & Deployment

### Docker Compose Setup

**Kafka Stack** (infrastructure/docker-compose.yml):

- Zookeeper (coordination)
- Kafka Broker (message broker)
- Kafdrop (UI for monitoring topics)
- Prometheus (metrics collection)
- Grafana (metrics visualization)

### Main App Ports

- **8080** - Quarkus main application
- **8081** - Quarkus consumer application
- **8161** - Kafdrop (Kafka UI)
- **9090** - Prometheus
- **3000** - Grafana

---

## 🚀 Technology Stack

- **Java 21** - Language
- **Quarkus 3.9.5** - Framework
- **Kafka** - Event broker
- **SmallRye Reactive Messaging** - Kafka consumer/producer API
- **Kafka Streams** - Stream processing
- **Qute** - Template engine (HTML UI)
- **Micrometer** - Metrics collection
- **Maven** - Build tool

---

## 📚 Project Structure

```
.
├── src/main/java/upjv/insset/
│   ├── Main.java                          # Entry point & architecture docs
│   ├── api/
│   │   ├── order/                         # Order REST API & service
│   │   ├── shop/                          # Shop UI
│   │   ├── analytics/                     # Analytics REST API
│   │   └── stock/                         # Stock management
│   ├── kafka/
│   │   ├── consumers/                     # Consumer services
│   │   ├── events/                        # Event classes
│   │   ├── services/                      # Partition listeners
│   │   └── topology/                      # Kafka Streams topology
│   └── shared/
│       ├── infrastructure/                # Catalog, config
│       └── model/                         # Shared models & enums
│
├── consumer/                              # Separate consumer app
│   └── src/main/java/upjv/insset/kafka/
│       └── consumers/                     # Additional consumer services
│
├── infrastructure/
│   ├── docker-compose.yml                 # Kafka stack
│   └── grafana/                           # Monitoring dashboards
│
└── README_PARTITIONS.md                   # Rebalancing documentation
```

---

## 🎓 Learning Objectives

This project demonstrates:

1. **Event-Driven Architecture** - Decoupled services via Kafka topics
2. **Saga Pattern** - Distributed transactions without 2PC
3. **Choreography** - Services react to events autonomously
4. **Kafka Concepts**:
   - Topics, partitions, consumer groups
   - Message keys for ordering guarantees
   - Consumer group rebalancing
   - Kafka Streams topology (KStream, KTable, state stores)
   - Interactive Queries for real-time aggregations
5. **Reactive Messaging** - SmallRye API for async I/O
6. **Monitoring** - Metrics, Grafana dashboards, consumer lag tracking
7. **Quarkus Features** - CDI, REST API, reactive extensions

---

## 🔗 Related Documentation

- [Partition & Rebalancing Guide](README_PARTITIONS.md)
- [Kafka Config Analysis](ANALYSIS_KAFKA_CONFIG.md)
- [Consumer Group Guide](CONSUMER_GROUP_GUIDE.md)
- [Testing Guide](TESTING_GUIDE.md)
- [Performance Testing](infrastructure/PERF_TESTING_GUIDE.md)
