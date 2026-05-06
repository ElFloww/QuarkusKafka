# Quick Diagnostic Checklist - RANK_VERIFIED → STOCK_RESERVED Issue

## Current Status

- ✅ rank-events topic has 3 partitions
- ✅ Gameync consumes orders-events → publishes rank-events
- ❓ **StockService consumes rank-events?** (NEEDS VERIFICATION)
- ✅ PaymentService consumes stock-events → publishes payment-events
- ✅ OrderConfirmation consumes payment-events

---

## Root Cause Hypothesis

The transactions are stuck at RANK_VERIFIED because **StockService is NOT receiving messages from rank-events**.

This is likely because:

1. **Consumer group `stock-service-grp` has no active members** (service crashed/not started)
2. **Deserialization failing silently** (failure-strategy=ignore masks errors)
3. **Message partition assignment issue** (messages stuck on partition not assigned to this consumer)
4. **Thread pool exhaustion** (all worker threads blocked, new messages queued indefinitely)

---

## Immediate Tests

### Test 1: Verify StockService Consumer Status

```bash
docker logs tuuuur-merch 2>&1 | grep -i "stock\|rank-in"
```

Look for:

- ✅ "StockService" initialization
- ✅ "@Incoming(\"rank-in\") detected"
- ❌ Any deserialization errors
- ❌ Any thread pool warnings

### Test 2: Check Consumer Group Status

```bash
docker exec tuuuur-kafka kafka-consumer-groups \
  --bootstrap-server kafka:29092 \
  --group stock-service-grp \
  --describe
```

**Expected Output:**

```
TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG  CONSUMER-ID
rank-events     0          123             150             27   consumer-1
rank-events     1          100             102             2    consumer-1
rank-events     2          95              98              3    consumer-1
```

**Bad Output (indicates broken consumer):**

```
Consumer group 'stock-service-grp' does not exist
```

### Test 3: Verify Messages Are Being Published to rank-events

```bash
docker exec tuuuur-kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic rank-events \
  --from-beginning \
  --max-messages 3
```

Should output JSON like:

```json
{"orderId":"ord-xxx","rankOk":true,"itemName":"tshirt-bronze",...}
```

### Test 4: Check Metrics (if app is running)

```bash
curl -s http://localhost:8080/q/metrics | grep -E "(tuuuur\.stock|tuuuur\.gamesync)"
```

Look for:

- `tuuuur.gamesync.rank.ok` counter increasing
- `tuuuur.stock.reserved` counter increasing
- `tuuuur.stock.failed` counter (check if errors)

### Test 5: Check Application Logs

```bash
docker logs tuuuur-merch 2>&1 | tail -100 | grep -E "(ERROR|FAILED|Exception|WARN)"
```

---

## Most Likely Causes in Order

### 1️⃣ **StockService Bean Not Initialized**

- Check logs: look for "StockService" or "stock-service-grp" messages
- If missing: Bean is not being loaded/scanned

**Fix**: Verify `@ApplicationScoped` and package scanning configurations

### 2️⃣ **Consumer Group Never Polled Messages**

- Run: `kafka-consumer-groups --describe --group stock-service-grp`
- If LAG is extremely high or offsets never increase: consumer is dead

**Fix**: Restart application

### 3️⃣ **Deserialization Exceptions (Silent Failure)**

- Check logs: `RankEventDeserializer` or `Cannot deserialize`
- Note: `failure-strategy=ignore` HIDES these errors
- Messages are skipped, offsets move forward, but nothing gets processed

**Fix**: Change `failure-strategy=ignore` to `failure-strategy=dead-letter-queue` or `fail` to make errors visible

### 4️⃣ **Partition Assignment Wrong**

- Run: `kafka-consumer-groups --describe --group stock-service-grp`
- If empty (no partitions listed): consumer is inactive
- If only 1 partition assigned: other partitions' messages ignored

**Fix**: Ensure consumer is running and healthy

### 5️⃣ **Thread Pool Starvation from @Blocking**

- Each `@Incoming` + `@Blocking` consumer uses a worker thread
- If thread pool exhausted: new messages queue forever
- Symptoms: messages in lag but not being processed

**Fix**: Increase thread pool size in `quarkus.thread-pool.core-threads`

---

## Configuration Reference

### [src/main/resources/application.properties] - Lines 67-75

```properties
# ── STOCK SERVICE ──
mp.messaging.incoming.rank-in.connector=smallrye-kafka
mp.messaging.incoming.rank-in.topic=rank-events
mp.messaging.incoming.rank-in.group.id=stock-service-grp
mp.messaging.incoming.rank-in.value.deserializer=upjv.insset.shared.model.RankEventDeserializer
mp.messaging.incoming.rank-in.auto.offset.reset=earliest
mp.messaging.incoming.rank-in.failure-strategy=ignore  # ⚠️ HIDES ERRORS!
```

### [src/main/java/upjv/insset/api/stock/StockService.java]

```java
@Incoming("rank-in")   // Must match application.properties channel name
@Blocking              // Required for blocking operations
public CompletionStage<Void> onRankVerified(Message<RankVerifiedEvent> message)
```

---

## Test Commands (Ready to Copy-Paste)

```bash
# Check if StockService started
docker logs tuuuur-merch 2>&1 | grep "StockService"

# Check consumer group exists and has active members
docker exec tuuuur-kafka kafka-consumer-groups \
  --bootstrap-server kafka:29092 --group stock-service-grp --describe

# See recent rank-events messages
docker exec tuuuur-kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 --topic rank-events \
  --from-beginning --max-messages 5

# Check stock metrics
curl http://localhost:8080/q/metrics 2>/dev/null | \
  grep -E "tuuuur_stock|tuuuur_gamesync" | head -20

# Real-time log monitoring
docker logs -f tuuuur-merch 2>&1 | grep -E "StockService|stock-service-grp|RankVerified"
```

---
