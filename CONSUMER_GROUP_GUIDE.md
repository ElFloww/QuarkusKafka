# Consumer Groupe avec Partitions – Guide Complet

## 📋 Vue d'ensemble

Vous avez maintenant un système complet de **Consumer Group avec gestion des Partitions** en place. Voici ce qui a été créé :

### Fichiers créés/modifiés :

1. **`OrderPartitionedConsumer.java`** → Consumer démonstration
2. **`PartitionRebalanceListener.java`** → Service de monitoring des rebalances
3. **`PartitionMonitoringResource.java`** → API REST pour surveiller les partitions
4. **`application.properties`** → Configuration SmallRye aggiorée

---

## 🎯 Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Topic: orders-events (3 partitions par défaut)         │
│  ┌──────────────────────────────────────────────────┐   │
│  │ P0 │ P1 │ P2 │ P0 │ P1 │ P2 │ P0 │ P1 │ P2 │... │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
              │              │              │
              ↓              ↓              ↓
   ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
   │ Instance 1       │  │ Instance 2       │  │ Instance 3       │
   │ ┌──────────────┐ │  │ ┌──────────────┐ │  │ ┌──────────────┐ │
   │ │ Consumer P0  │ │  │ │ Consumer P1  │ │  │ │ Consumer P2  │ │
   │ └──────────────┘ │  │ └──────────────┘ │  │ └──────────────┘ │
   │ group.id:        │  │ group.id:        │  │ group.id:        │
   │ orders-partition-│  │ orders-partition-│  │ orders-partition-│
   │ processor-grp    │  │ processor-grp    │  │ processor-grp    │
   └──────────────────┘  └──────────────────┘  └──────────────────┘
```

**Consumer Group** = `orders-partition-processor-grp`

- Toutes les instances partagent le même group ID
- Kafka assigne les partitions automatiquement
- Si une instance tombe → rebalance automatique

---

## 🔧 Configuration

### Dans `application.properties` :

```properties
# Channel de consommation
mp.messaging.incoming.orders-partitioned-in.connector=smallrye-kafka
mp.messaging.incoming.orders-partitioned-in.topic=orders-events
mp.messaging.incoming.orders-partitioned-in.group.id=orders-partition-processor-grp

# Comportement des partitions
mp.messaging.incoming.orders-partitioned-in.max.poll.records=100
    → Max 100 messages par batch

mp.messaging.incoming.orders-partitioned-in.session.timeout.ms=30000
    → Instance morte si inactive > 30 secondes

mp.messaging.incoming.orders-partitioned-in.heartbeat.interval.ms=10000
    → Ping au broker tous les 10 secondes

# Rebalance listener
mp.messaging.incoming.orders-partitioned-in.consumer-rebalance-listener.enabled=true
    → Active les hooks onPartitionsRevoked/onPartitionsAssigned
```

---

## 🚀 Utilisation

### 1️⃣ Consumer (traitement des messages)

```java
@ApplicationScoped
public class OrderPartitionedConsumer {

    @Incoming("orders-partitioned-in")
    @Blocking
    public CompletionStage<Void> processOrderWithPartitionTracking(
            Message<OrderCreatedEvent> message) {

        OrderCreatedEvent event = message.getPayload();

        if (message instanceof KafkaMessage<?> kafkaMsg) {
            int partition = kafkaMsg.getPartition();  // ← Partition traitée
            long offset = kafkaMsg.getOffset();       // ← Position dans la partition
            String topic = kafkaMsg.getTopic();

            // Traiter le message
            LOG.infof("Processing from partition %d at offset %d",
                     partition, offset);
        }

        return message.ack();  // ← Valider l'offset
    }
}
```

### 2️⃣ Monitoring (via API REST)

```bash
# Voir l'état actuel des partitions
curl http://localhost:8080/api/partitions/status

# Réponse :
{
  "status": "BALANCED",
  "assignedPartitions": [0, 2],
  "partitionCount": 2,
  "timestamp": 1712973456000,
  "instanceId": "tuuuur-merch-1"
}

# Compter les partitions
curl http://localhost:8080/api/partitions/count
# {"partitionCount": 2, "capacity": 10}

# Health check
curl http://localhost:8080/api/partitions/health
# {"ready": true, "partitions": 2}
```

### 3️⃣ Rebalance Listener (automatique)

Les logs montreront :

```
⚠️  REBALANCE : Partitions RÉVOQUÉES [instance perdue les partitions]
   Partitions perdues : [P0, P2]
   ...
✅ REBALANCE COMPLÉTÉ : Partitions ASSIGNÉES à cette instance
   Partitions reçues : [P1]
   ...
```

---

## 📈 Scalabilité Horizontale

### Déployer 3 instances du service

```bash
# Instance 1
java -jar tuuuur-merch.jar

# Instance 2 (nouveau terminal)
java -jar tuuuur-merch.jar

# Instance 3 (nouveau terminal)
java -jar tuuuur-merch.jar
```

**Résultat** :

- Topic `orders-events` a 3 partitions (P0, P1, P2)
- Chaque instance traite 1 partition exclusive
- Si Instance 2 s'arrête → Rebalance automatique → P1 redistribuée

### Avec Docker Compose

```yaml
services:
  merch-service:
    image: tuuuur-merch:latest
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    deploy:
      replicas: 3 # 3 instances
```

Kubernetes gérera la scalabilité + Kafka assurera le rebalancing.

---

## 🔍 Comportement du Rebalancing

### Avant rebalance (équilibré)

```
Group: orders-partition-processor-grp
├─ Instance 1: [P0]
├─ Instance 2: [P1]
└─ Instance 3: [P2]
```

### Instance 2 s'arrête

```
⏳ REBALANCING EN COURS (stop momentané du traitement)
```

### Après rebalance (rééquilibré)

```
Group: orders-partition-processor-grp
├─ Instance 1: [P0, P1]  ← P0 + P1 (redistribution auto)
└─ Instance 3: [P2]
```

**Durée** : ~5-10 secondes typiquement  
**Impact** : Pas de messages perdus (offsets persistés)

---

## 💾 Gestion des Offsets

### Offset = position dans une partition

```
Partition P0:
  Message 0 (offset=0) : Order-1
  Message 1 (offset=1) : Order-2
  Message 2 (offset=2) : Order-3
                         ↑
                    Consumer group
               dernier offset ACKé = 2
```

### Que se passe-t-il au redémarrage ?

```bash
# Scenario 1: Restart avec ack() précédent
auto.offset.reset=earliest
→ Reprend depuis offset=3 (suivant le dernier ACKé)
  → Pas de redoublons

# Scenario 2: Pas d'offset précédent (nouvelle instance)
auto.offset.reset=earliest
→ Commence depuis offset=0 (début du topic)
  → Peut rejouer tous les anciens messages

# Scenario 3: auto.offset.reset=latest
→ Commence depuis le dernier message du topic
  → Ignore tous les anciens messages
```

**Votre config actuelle** :

```properties
mp.messaging.incoming.orders-partitioned-in.auto.offset.reset=earliest
```

→ Rejoue depuis le début si pas d'offset ✓ Idéal pour une démo

---

## 🐛 Debugging

### 1️⃣ Voir les consumer groups actifs

```bash
# Via Kafka CLI (si disponible)
kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --list

# Résultat :
# orders-partition-processor-grp
# order-service-payment-grp
# gamesync-service-grp
# stock-service-grp
# ...
```

### 2️⃣ Détails d'un groupe

```bash
kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orders-partition-processor-grp \
  --describe

# Output :
# GROUP                             TOPIC         PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# orders-partition-processor-grp    orders-events 0          42              45              3
# orders-partition-processor-grp    orders-events 1          38              38              0
# orders-partition-processor-grp    orders-events 2          55              55              0
```

### 3️⃣ Logs Quarkus

```bash
# Relancer avec debug
mvn quarkus:dev -Dquarkus.log.level=DEBUG

# Regarder les messages du consumer :
[INFO] ✅ REBALANCE COMPLÉTÉ : Partitions ASSIGNÉES
[INFO] 🎯 ORDER PARTITIONED CONSUMER
[INFO]    Partition: 0, Offset: 42
```

---

## 🎓 Concepts Clés à Retenir

| Concept             | Explication                                                                |
| ------------------- | -------------------------------------------------------------------------- |
| **Consumer Group**  | Identifiant (`group.id`) pour un ensemble d'instances traitant ensemble    |
| **Partition**       | Division d'un topic (ex: 3 partitions, 3 instances = 1 partition/instance) |
| **Rebalancing**     | Redistribution des partitions quand une instance monte/descend             |
| **Offset**          | Position d'un message dans une partition (0, 1, 2, ...)                    |
| **ACK**             | Valider l'offset → signal au broker qu'on a traité le message              |
| **Heartbeat**       | Ping régulier pour signaler : "je suis vivant"                             |
| **Session Timeout** | Si pas de heartbeat > N ms → instance déclarée morte                       |
| **LAG**             | Retard = (LOG-END-OFFSET - CURRENT-OFFSET)                                 |

---

## ✅ Tâches suivantes

Pour aller plus loin :

1. **Tester le failover** : arrêter une instance → observer rebalance
2. **Ajouter des métriques** : LAG en Prometheus/Grafana
3. **Configurer une DLQ** : topic pour les messages rejetés
4. **State Stores** : Kafka Streams pour agrégations par partition
5. **Exactement-once** : plutôt que "au-moins-une-fois"

---

## 📚 Ressources

- [Kafka Consumer Configuration](https://kafka.apache.org/documentation/#consumerconfigs)
- [SmallRye Reactive Messaging](https://smallrye.io/smallrye-reactive-messaging/)
- [Quarkus + Kafka](https://quarkus.io/guides/kafka)
- [Rebalancing Protocol](https://kafka.apache.org/documentation/#consumerconfigs_session.timeout.ms)
