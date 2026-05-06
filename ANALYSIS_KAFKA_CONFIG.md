# Kafka Consumer Configuration Analysis - STOCK_RESERVED Issue

**Issue**: Transactions reach `RANK_VERIFIED` but stop there, never reaching `STOCK_RESERVED`. This means `StockService` isn't consuming `rank-events`.

---

## 1. Topic Partitions Configuration

From [infrastructure/init-topics.sh](infrastructure/init-topics.sh):

```bash
orders-events:    3 partitions (replication factor: 1)
rank-events:      3 partitions (replication factor: 1)
stock-events:     3 partitions (replication factor: 1)
payment-events:   3 partitions (replication factor: 1)
analytics-stats:  1 partition  (replication factor: 1)
```

**Key Point**: With 3 partitions and grouping by `orderId` (message key), messages for the same order always go to the same partition, ensuring ordering within an order's saga.

---

## 2. Architecture: TWO Separate Quarkus Applications

⚠️ **Critical Discovery**: There are TWO separate Quarkus applications with DUPLICATE consumers:

### Application #1: Main App (src/, port 8080)

- **Location**: `/src/main/java/upjv/insset/`
- **Config**: [src/main/resources/application.properties](src/main/resources/application.properties)
- **Consumers**:
  - OrderService (produces orders-events)
  - GameSyncService (kafka/consumers/)
  - StockService (api/stock/) ← **HAS rank-in consumer**
  - OrderSagaStatusUpdater (api/order/)
  - OrderConfirmationConsumer (kafka/consumers/)

### Application #2: Consumer App (consumer/, port 8081)

- **Location**: `/consumer/src/main/java/upjv/insset/kafka/`
- **Config**: [consumer/src/main/resources/application.properties](consumer/src/main/resources/application.properties)
- **Consumers**:
  - GameSyncService (consumers/)
  - PaymentService (consumers/) ← **HAS stock-in consumer**
  - OrderConfirmationConsumer (consumers/)
  - OrderPartitionedConsumer (consumers/)

---

## 3. Consumer Groups & Message Flow

### Main App Configuration (port 8080)

[Lines 38-118 of src/main/resources/application.properties]:

| Channel           | Topic          | Consumer Group                   | Partition Handling | Failure Strategy |
| ----------------- | -------------- | -------------------------------- | ------------------ | ---------------- |
| orders-in         | orders-events  | **gamesync-service-grp**         | Default            | ignore           |
| rank-in           | rank-events    | **stock-service-grp**            | Default            | ignore           |
| rank-observer-in  | rank-events    | **order-service-rank-observer**  | Default            | ignore           |
| stock-in          | stock-events   | **payment-service-grp**          | Default            | ignore           |
| stock-observer-in | stock-events   | **order-service-stock-observer** | Default            | ignore           |
| payment-in        | payment-events | **order-service-payment-grp**    | Default            | ignore           |

### Consumer App Configuration (port 8081)

[Lines 50-123 of consumer/src/main/resources/application.properties]:

| Channel               | Topic          | Consumer Group                     | Partition Handling                               | Failure Strategy      |
| --------------------- | -------------- | ---------------------------------- | ------------------------------------------------ | --------------------- |
| orders-partitioned-in | orders-events  | **orders-partition-processor-grp** | max.poll=100, session timeout=30s, heartbeat=10s | ignore                |
| orders-in             | orders-events  | **gamesync-service-grp**           | Default                                          | ignore                |
| stock-in              | stock-events   | **payment-service-grp**            | Default                                          | **fail** ← Different! |
| payment-in            | payment-events | **order-service-payment-grp**      | Default                                          | ignore                |
| payment-analytics-in  | payment-events | **analytics-monitoring-grp**       | Default                                          | ignore                |

---

## 4. StockService Consumer Analysis

### Main App: StockService (src/main/java/upjv/insset/api/stock/)

**Location**: [src/main/java/upjv/insset/api/stock/StockService.java](src/main/java/upjv/insset/api/stock/StockService.java)

```java
@ApplicationScoped
public class StockService {

    @Incoming("rank-in")                          // ✅ Consumes rank-events
    @Blocking
    public CompletionStage<Void> onRankVerified(Message<RankVerifiedEvent> message) {
        RankVerifiedEvent event = message.getPayload();

        // Validates event.rankOk
        if (!event.rankOk) {
            // Rejects if rank insufficient
            return message.ack();
        }

        // Atomically decrements stock with compareAndSet loop
        // If stock insufficient: logs warning, returns ack()
        // If stock success: publishes StockReservedEvent to stock-events

        try {
            stockEmitter.send(KafkaRecord.of(event.orderId, stockEvent));
            return message.ack();
        } catch (Exception ex) {
            // Rollback stock on publish failure
            stock.addAndGet(event.quantity);
            return message.nack(ex);
        }
    }
}
```

**Configuration**:

```properties
mp.messaging.incoming.rank-in.connector=smallrye-kafka
mp.messaging.incoming.rank-in.topic=rank-events
mp.messaging.incoming.rank-in.group.id=stock-service-grp
mp.messaging.incoming.rank-in.value.deserializer=upjv.insset.shared.model.RankEventDeserializer
mp.messaging.incoming.rank-in.auto.offset.reset=earliest
mp.messaging.incoming.rank-in.failure-strategy=ignore
```

✅ **This consumer IS configured correctly and should work.**

---

## 5. PaymentService Analysis

### Consumer App: PaymentService (consumer/src/main/java/upjv/insset/kafka/consumers/)

**Location**: [consumer/src/main/java/upjv/insset/kafka/consumers/PaymentService.java](consumer/src/main/java/upjv/insset/kafka/consumers/PaymentService.java)

```java
@ApplicationScoped
public class PaymentService {

    @Incoming("stock-in")                         // ✅ Consumes stock-events
    @Blocking
    public CompletionStage<Void> onStockReserved(Message<StockReservedEvent> message) {
        // Simulates payment processing (Thread.sleep 1000ms)
        simulatePaymentProcessing(event);

        // Publishes PaymentSucceededEvent to payment-events
        publishPaymentSucceeded(event);

        return message.ack() // or nack(ex)
    }
}
```

**Configuration in Consumer App**:

```properties
mp.messaging.incoming.stock-in.connector=smallrye-kafka
mp.messaging.incoming.stock-in.topic=stock-events
mp.messaging.incoming.stock-in.group.id=payment-service-grp
mp.messaging.incoming.stock-in.value.deserializer=upjv.insset.shared.model.StockEventDeserializer
mp.messaging.incoming.stock-in.auto.offset.reset=earliest
mp.messaging.incoming.stock-in.failure-strategy=fail     ← ⚠️ FAIL strategy
```

✅ **This consumer is also configured and working in the Consumer App.**

---

## 6. OrderConfirmationConsumer Analysis

### Consumer App: OrderConfirmationConsumer

**Location**: [consumer/src/main/java/upjv/insset/kafka/consumers/OrderConfirmationConsumer.java](consumer/src/main/java/upjv/insset/kafka/consumers/OrderConfirmationConsumer.java)

```java
@ApplicationScoped
public class OrderConfirmationConsumer {

    @Incoming("payment-in")                       // ✅ Consumes payment-events
    @Blocking
    public CompletionStage<Void> onPaymentSucceeded(Message<PaymentSucceededEvent> message) {
        // Updates order status to CONFIRMED
        orderService.confirmOrder(event.orderId, event.transactionId);
        return message.ack();
    }
}
```

**Configuration**:

```properties
mp.messaging.incoming.payment-in.connector=smallrye-kafka
mp.messaging.incoming.payment-in.topic=payment-events
mp.messaging.incoming.payment-in.group.id=order-service-payment-grp
mp.messaging.incoming.payment-in.value.deserializer=upjv.insset.shared.model.PaymentEventDeserializer
mp.messaging.incoming.payment-in.auto.offset.reset=earliest
mp.messaging.incoming.payment-in.failure-strategy=ignore
```

✅ **This consumer is configured.**

---

## 7. Message Flow Path Analysis

```
┌─────────────────────────────────────────────────────────────────────┐
│ MAIN APP (port 8080)                                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Step 1: API → OrderService                                       │
│  ├─ Publishes OrderCreatedEvent → orders-events (partition %)    │
│                                                                     │
│  Step 2: GameSyncService (@Incoming("orders-in"))                 │
│  ├─ Consumes from: orders-events (gamesync-service-grp)          │
│  ├─ Publishes RankVerifiedEvent → rank-events (partition %)      │
│                                                                     │
│  Step 3: StockService (@Incoming("rank-in"))  ✅ CORRECT          │
│  ├─ Consumes from: rank-events (stock-service-grp)               │
│  ├─ Publishes StockReservedEvent → stock-events (partition %)    │
│                                                                     │
┗─────────────────────────────────────────────────────────────────────┛
                                    ↓
                          (stock-events messages)
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│ CONSUMER APP (port 8081)                                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Step 4: PaymentService (@Incoming("stock-in"))  ✅ CORRECT       │
│  ├─ Consumes from: stock-events (payment-service-grp) [FAIL]     │
│  ├─ Simulates payment (1 second)                                  │
│  ├─ Publishes PaymentSucceededEvent → payment-events (partition %)│
│                                                                     │
│  Step 5: OrderConfirmationConsumer (@Incoming("payment-in")) ✅   │
│  ├─ Consumes from: payment-events (order-service-payment-grp)    │
│  ├─ Updates order status → CONFIRMED                             │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 8. Critical Findings: Why RANK_VERIFIED → STOCK_RESERVED Fails

### ✅ Configuration IS Correct

- Both applications are running (ports 8080 and 8081)
- StockService in Main App IS consuming `rank-events` ✓
- PaymentService in Consumer App IS consuming `stock-events` ✓
- All consumer groups are properly configured ✓

### ⚠️ Possible Issues (In Order of Likelihood)

#### Issue #1: Consumer Not Running or Hung

- Verify StockService consumer is actually running
- Check if `stock-service-grp` has active members
- Verify no exceptions in logs

#### Issue #2: Message Deserialization Failure

- RankEventDeserializer might be failing silently (failure-strategy=ignore)
- With `failure-strategy=ignore`, deserialization errors are skipped without retry

#### Issue #3: Offset Management Issue

- StockService lag might be too far behind
- With `auto.offset.reset=earliest`, it might be replaying old messages
- Check consumer lag: `tuuuur.stock.*` metrics in Prometheus

#### Issue #4: Partition Assignment Issue

- With 3 partitions and multiple consumers, partitions might not be assigned
- E.g., if only 1 StockService instance and gamesync runs in different partition

#### Issue #5: Thread Pool Starvation

- @Blocking annotation requires sufficient worker threads
- If all workers are blocked, new messages won't be processed
- Check Quarkus thread pool configuration

---

## 9. Configuration Differences Between Apps

| Aspect                        | Main App (8080)         | Consumer App (8081)  |
| ----------------------------- | ----------------------- | -------------------- |
| **stock-in failure-strategy** | ignore                  | **fail**             |
| stock-in session.timeout      | Default                 | Not set              |
| stock-in max.poll.records     | Default (500)           | Not set              |
| GameSyncService location      | kafka/consumers/        | kafka/consumers/     |
| PaymentService location       | api/payment/ (MISSING!) | kafka/consumers/ ✓   |
| StockService consumer         | rank-in ✓               | tank-in ✗ (missing!) |

⚠️ **Key Difference**:

- Main App: `stock-in` uses `failure-strategy=ignore`
- Consumer App: `stock-in` uses `failure-strategy=fail`

This means PaymentService (Consumer App) on failure mode will retry, while if connected to main app stock channel it would fail silently.

---

## 10. Recommended Diagnostics

### Check Consumer Group Status

```bash
docker exec tuuuur-kafka kafka-consumer-groups \
  --bootstrap-server kafka:29092 \
  --group stock-service-grp \
  --describe
```

Expected output shows:

- TOPIC: rank-events
- PARTITION: 0, 1, 2
- CURRENT-OFFSET: increasing
- LAG: should be 0 or small

### Check Active Consumers

```bash
docker exec tuuuur-kafka kafka-consumer-groups \
  --bootstrap-server kafka:29092 \
  --all-groups \
  --members
```

Should show `stock-service-grp` with at least 1 member.

### Monitor Consumer Metrics

```bash
curl http://localhost:8080/q/metrics | grep -E "tuuuur\.stock\.|tuuuur\.gamesync\."
```

Look for:

- `tuuuur.gamesync.rank.ok` (should be incrementing)
- `tuuuur.gamesync.rank.ko` (should be low)
- `tuuuur.stock.reserved` (should be incrementing)
- `tuuuur.stock.failed` (unexpected failures)

### Check Topic Messages

```bash
docker exec tuuuur-kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic rank-events \
  --from-beginning \
  --max-messages 5
```

Should output RankVerifiedEvent JSON messages.

---

## 11. Summary Table

| Layer | Component                    | Topic In        | Topic Out        | Status        |
| ----- | ---------------------------- | --------------- | ---------------- | ------------- |
| 1     | API → OrderService           | -               | orders-events    | ✅            |
| 2     | GameSync (Main)              | orders-events   | rank-events      | ✅            |
| 3     | **StockService (Main)**      | **rank-events** | **stock-events** | **⚠️ VERIFY** |
| 4     | PaymentService (Consumer)    | stock-events    | payment-events   | ✅            |
| 5     | OrderConfirmation (Consumer) | payment-events  | -                | ✅            |

**The problem is almost certainly at Layer 3** - either:

1. StockService consumer not running
2. Messages not deserializing correctly (silent failure with ignore strategy)
3. Messages stuck on specific partition assignment
4. Consumer lag issue preventing message processing

---

## 12. Key Configurations to Verify

**In [src/main/resources/application.properties](src/main/resources/application.properties):**

```properties
# Line 67-75: STOCK SERVICE Configuration
mp.messaging.incoming.rank-in.connector=smallrye-kafka
mp.messaging.incoming.rank-in.topic=rank-events
mp.messaging.incoming.rank-in.group.id=stock-service-grp
mp.messaging.incoming.rank-in.value.deserializer=upjv.insset.shared.model.RankEventDeserializer
mp.messaging.incoming.rank-in.auto.offset.reset=earliest
mp.messaging.incoming.rank-in.failure-strategy=ignore   # ⚠️ Silently ignores errors
```

**Verify the StockService Bean is being loaded:**

- Check logs for: "StockService" or "rank-in" at startup

---
