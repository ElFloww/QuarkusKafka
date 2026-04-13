# 📦 Consumer Groupe avec Partitions – Résumé Visuel

## 🏗️ Architecture créée

```
┌─────────────────────────────────────────────────────────────────┐
│                  KAFKA CLUSTER                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Topic: orders-events                                     │   │
│  │ ┌────────────────────────────────────────────────────┐   │   │
│  │ │ P0: [msg0, msg1, msg2, ...]                       │   │   │
│  │ │ P1: [msg0, msg1, msg2, ...]                       │   │   │
│  │ │ P2: [msg0, msg1, msg2, ...]                       │   │   │
│  │ └────────────────────────────────────────────────────┘   │   │
│  │                                                            │   │
│  │ Consumer Group: orders-partition-processor-grp           │   │
│  │ ├─ group.id          = orders-partition-processor-grp    │   │
│  │ ├─ max.poll.records  = 100                               │   │
│  │ ├─ session.timeout   = 30000ms                           │   │
│  │ └─ heartbeat.interval= 10000ms                           │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
              │                    │                    │
              ↓                    ↓                    ↓
┌──────────────────────┐ ┌──────────────────────┐ ┌──────────────────────┐
│  INSTANCE 1 (Port    │ │  INSTANCE 2 (Port    │ │  INSTANCE 3 (Port    │
│       8080)          │ │       8081)          │ │       8082)          │
├──────────────────────┤ ├──────────────────────┤ ├──────────────────────┤
│ ┌──────────────────┐ │ │ ┌──────────────────┐ │ │ ┌──────────────────┐ │
│ │ OrderPartitioned │ │ │ │ OrderPartitioned │ │ │ │ OrderPartitioned │ │
│ │   Consumer       │ │ │ │   Consumer       │ │ │ │   Consumer       │ │
│ └──────────────────┘ │ │ └──────────────────┘ │ │ └──────────────────┘ │
│ Partition: [0]       │ │ Partition: [1]       │ │ Partition: [2]       │
│                      │ │                      │ │                      │
│ ┌──────────────────┐ │ │ ┌──────────────────┐ │ │ ┌──────────────────┐ │
│ │ PartitionRebalance   │ │ │ PartitionRebalance   │ │ │ PartitionRebalance   │
│ │    Listener      │ │ │ │    Listener      │ │ │ │    Listener      │
│ └──────────────────┘ │ │ └──────────────────┘ │ │ └──────────────────┘ │
│                      │ │                      │ │                      │
│ GET /api/partitions/ │ │ GET /api/partitions/ │ │ GET /api/partitions/ │
│      status          │ │      status          │ │      status          │
│      count           │ │      count           │ │      count           │
│      assigned        │ │      assigned        │ │      assigned        │
│      health          │ │      health          │ │      health          │
└──────────────────────┘ └──────────────────────┘ └──────────────────────┘
```

---

## 📁 Fichiers créés / Modifiés

### 1. Configuration

| Fichier                                     | Description                         |
| ------------------------------------------- | ----------------------------------- |
| `src/main/resources/application.properties` | Configuration SmallRye + partitions |

### 2. Consumer (Traitement des messages)

| Fichier                                                           | Description                 |
| ----------------------------------------------------------------- | --------------------------- |
| `src/main/java/.../kafka/consumers/OrderPartitionedConsumer.java` | **NOUVEAU** Consumer groupe |

### 3. Services (Monitoring & Configuration)

| Fichier                                                                | Description                   |
| ---------------------------------------------------------------------- | ----------------------------- |
| `src/main/java/.../kafka/services/PartitionRebalanceListener.java`     | **NOUVEAU** Hooks rebalancing |
| `src/main/java/.../kafka/config/PartitionedConsumerConfiguration.java` | **NOUVEAU** Config wiring     |

### 4. API REST (Monitoring)

| Fichier                                                            | Description                |
| ------------------------------------------------------------------ | -------------------------- |
| `src/main/java/.../api/analytics/PartitionMonitoringResource.java` | **NOUVEAU** Endpoints REST |

### 5. Documentation

| Fichier                   | Description                 |
| ------------------------- | --------------------------- |
| `CONSUMER_GROUP_GUIDE.md` | **NOUVEAU** Guide complet   |
| `TESTING_GUIDE.md`        | **NOUVEAU** Tests pratiques |

---

## 🎯 Points clés de l'implémentation

### ✅ Consumer Group

```properties
group.id=orders-partition-processor-grp
```

- Identifiant unique du groupe
- Toutes les instances partagent ce group ID
- Kafka gère l'assignement des partitions

### ✅ Partitions

```properties
max.poll.records=100           # Batch size
session.timeout.ms=30000       # Détection mort
heartbeat.interval.ms=10000    # Ping au broker
```

- Chaque instance traite 1+ partitions exclusivement
- Rebalancing automatique si instance tombe
- Offsets gérés automatiquement

### ✅ Monitoring

```java
@GET @Path("/api/partitions/status")
// Retourne : partitions assignées, timestamp, instance ID
```

- API REST pour surveiller l'état
- Logs détaillés des rebalances
- Health check pour Kubernetes

---

## 🚀 Utilisation rapide

### démarrer l'app

```bash
mvn quarkus:dev
```

### Vérifier les partitions assignées

```bash
curl http://localhost:8080/api/partitions/status | jq
```

### Tester avec 3 instances (scalabilité)

```bash
# Terminal 1
PORT=8080 mvn quarkus:dev

# Terminal 2
PORT=8081 mvn quarkus:dev

# Terminal 3
PORT=8082 mvn quarkus:dev

# Observer les rebalances dans les logs
```

### Arrêter une instance (failover)

```bash
# Ctrl+C dans Terminal 2
# Observer les logs : rebalance automatique

# Les partitions de Instance 2 sont redistribuées
```

---

## 📊 Flux de rebalancing

### 1️⃣ État initial

```
Instance A : [P0]
Instance B : [P1]
Instance C : [P2]
```

### 2️⃣ Instance B s'arrête → Rebalancing

```
⏳ REBALANCING EN COURS
   - onPartitionsRevoked() appelé sur A & C
   - Cleanup, commit offsets
```

### 3️⃣ Nouvel équilibre

```
Instance A : [P0, P1]  ← Reçoit P1 aussi
Instance C : [P2]      ← Garde P2
```

### 4️⃣ Reprendre le traitement

```
✅ Instance A traite P0 + P1
✅ Instance C traite P2
   Aucun message perdu (offsets persistés)
```

---

## 🔍 Debugging – Commandes Kafka utiles

### Voir le consumer group

```bash
docker exec -it tuuuur-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orders-partition-processor-grp \
  --describe
```

### Réinitialiser les offsets (⚠️ dev/test only)

```bash
docker exec -it tuuuur-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orders-partition-processor-grp \
  --topic orders-events \
  --reset-offsets \
  --to-earliest \
  --execute
```

---

## 📈 Concepts clés

| Concept             | Explication                                        |
| ------------------- | -------------------------------------------------- |
| **Consumer Group**  | `group.id` → identifie un ensemble d'instances     |
| **Partition**       | Divise un topic (P0, P1, P2, ...)                  |
| **Rebalancing**     | Redistribution auto des partitions (mount/crash)   |
| **Offset**          | Position dans une partition (0, 1, 2, ...)         |
| **ACK**             | Valider le traitement d'un message → commit offset |
| **LAG**             | Retard = (dernière msg - offset group)             |
| **Heartbeat**       | Ping pour dire "je suis vivant"                    |
| **Session Timeout** | Si pas de heartbeat → instance déclarée morte      |

---

## ✅ Checklist – Qu'a-t-on mis en place ?

- [x] Consumer groupe avec group ID unique
- [x] Configuration des partitions (max.poll.records, timeouts)
- [x] Auto-rebalancing onPartitionsRevoked/onPartitionsAssigned
- [x] Gestion des offsets (ACK manuel)
- [x] API REST pour monitoring
- [x] Logging détaillé des rebalances
- [x] Scalabilité horizontale (N instances = N partitions)
- [x] Documentation + guide de test

---

## 📚 Resources

- [Kafka Consumer Configuration](https://kafka.apache.org/documentation/#consumerconfigs)
- [SmallRye Reactive Messaging](https://smallrye.io/smallrye-reactive-messaging/)
- [Quarkus + Kafka](https://quarkus.io/guides/kafka)
- [Rebalancing Protocol](https://kafka.apache.org/documentation/#protocol_details)

---

## 🎓 Prochaines étapes

Pour aller plus loin :

1. **Ajouter une DLQ** (Dead Letter Queue) pour les messages échoués
2. **Configurer Prometheus/Grafana** pour les métriques LAG
3. **Implémenter des State Stores** (Kafka Streams si besoin d'agrégations)
4. **Tester l'exactly-once semantics** au lieu du "at-least-once" actuel
5. **Setup Kubernetes** avec auto-scaling basé sur le LAG
