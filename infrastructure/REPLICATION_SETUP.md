# Kafka Cluster en Réplication (3 Brokers)

## 📋 Vue d'ensemble

Vous avez maintenant un cluster Kafka local avec **3 brokers en réplication**, permettant de tester:

- ✅ La **réplication des partitions** entre brokers
- ✅ La **résilience** (failover en cas d'arrêt d'un broker)
- ✅ La **cohérence** avec les ISR (In-Sync Replicas)
- ✅ Le **leadership** automatique des partitions

## 🔧 Configuration

### Brokers

| Port | Hostname       | Node ID | Rôle                |
| ---- | -------------- | ------- | ------------------- |
| 9092 | kafka-broker-1 | 1       | Broker + Controller |
| 9093 | kafka-broker-2 | 2       | Broker + Controller |
| 9094 | kafka-broker-3 | 3       | Broker + Controller |

### Mode KRaft

- **CLUSTER_ID**: `rT63_zHrSiKRvyAuyi9IFA==` (identique pour tous)
- **Quorum**: 3 votants → nécessite 2 brokers opérationnels minimum
- **Pas de Zookeeper** → Kafka 4.0+ compliant

### Topics

Tous les topics sont créés avec:

- **Partitions**: 3
- **Replication Factor**: 3 (sauf analytics-stats: 1 partition)
- **Min ISR**: 2 (au minimum 2 replicas in-sync pour acknowledger)

## 🚀 Démarrage

```bash
cd infrastructure
docker-compose up -d
```

Vérifiez les 3 brokers:

```bash
docker-compose ps | grep kafka-broker
```

## 📊 Monitoring

### 1. Kafka UI (GUI)

- **URL**: http://localhost:8090
- Voir les brokers, topics, partitions, consumer groups
- Vérifier les ISR (In-Sync Replicas) et les leaders

### 2. Prometheus + Grafana

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/tuuuur2025)
- Métriques JMX de tous les brokers

## 🧪 Tests de Réplication

### Test 1: Vérifier les replicas d'un topic

```bash
docker run --rm --network tuuuur-net confluentinc/cp-kafka:7.6.0 \
  kafka-topics --bootstrap-server kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092 \
  --describe --topic orders-events
```

Vous verrez quelque chose comme:

```
Topic: orders-events
  Partition: 0    Leader: 1    Replicas: 1,2,3    Isr: 1,2,3
  Partition: 1    Leader: 2    Replicas: 2,3,1    Isr: 2,3,1
  Partition: 2    Leader: 3    Replicas: 3,1,2    Isr: 3,1,2
```

### Test 2: Test de failover - Arrêter un broker

```bash
# Arrêter kafka-broker-2
docker-compose stop kafka-broker-2

# Attendre 5-10 secondes et vérifier l'état
docker run --rm --network tuuuur-net confluentinc/cp-kafka:7.6.0 \
  kafka-topics --bootstrap-server kafka-broker-1:29092,kafka-broker-3:29092 \
  --describe --topic orders-events
```

**Résultat attendu**: Les ISR manquent le broker 2, mais le cluster reste opérationnel

```
Topic: orders-events
  Partition: 0    Leader: 1    Replicas: 1,2,3    Isr: 1,3      ← broker 2 disparu
  Partition: 1    Leader: 3    Replicas: 2,3,1    Isr: 3,1      ← nouveau leader
```

### Test 3: Relancer le broker

```bash
docker-compose up -d kafka-broker-2

# Après quelques secondes, vérifier que les ISR se restaurent
docker run --rm --network tuuuur-net confluentinc/cp-kafka:7.6.0 \
  kafka-topics --bootstrap-server kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092 \
  --describe --topic orders-events
```

### Test 4: Produire et consommer des messages

**Producteur**:

```bash
docker run -it --rm --network tuuuur-net confluentinc/cp-kafka:7.6.0 \
  kafka-console-producer --broker-list kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092 \
  --topic orders-events
# Tapez des messages et appuyez sur ENTER
```

**Consommateur**:

```bash
docker run --rm --network tuuuur-net confluentinc/cp-kafka:7.6.0 \
  kafka-console-consumer --bootstrap-server kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092 \
  --topic orders-events --from-beginning
```

**Test de résilience du consommateur**:

1. Lancez le consommateur ci-dessus
2. Arrêtez un broker: `docker-compose stop kafka-broker-2`
3. Le consommateur **continue de travailler** car il peut rediriger vers les autres brokers
4. Continuez à produire des messages depuis un autre terminal
5. Relancez le broker: `docker-compose up -d kafka-broker-2`

## 📝 Configuration Quarkus

La propriété `kafka.bootstrap.servers` dans [application.properties](../src/main/resources/application.properties) a été mise à jour:

```properties
kafka.bootstrap.servers=localhost:9092,localhost:9093,localhost:9094
```

Quarkus se connectera au premier broker disponible et gérera automatiquement la failover.

## ⚠️ Points Importants

### Min ISR (Minimum In-Sync Replicas)

```
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
```

- Si vous arrêtez 2 brokers simultanément → **pas de quorum** → cluster indisponible
- **Minimum 2 brokers sur 3 doivent être opérationnels**

### Replication Factor vs Available Brokers

- Avec 3 brokers et RF=3: toutes les partitions sont répliquées sur tous les brokers
- Si vous en avez besoin: réduire RF à 2 pour plus de flexibilité

### Script de Test

Exécutez le script fourni:

```bash
bash infrastructure/test-replication.sh
```

## 🛠️ Commandes Utiles

### État détaillé du cluster

```bash
docker run --rm --network tuuuur-net confluentinc/cp-kafka:7.6.0 \
  kafka-broker-api-versions --bootstrap-server kafka-broker-1:29092
```

### Consumer groups

```bash
docker run --rm --network tuuuur-net confluentinc/cp-kafka:7.6.0 \
  kafka-consumer-groups --bootstrap-server kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092 \
  --list
```

### Logs d'un broker

```bash
docker logs -f tuuuur-kafka-1
docker logs -f tuuuur-kafka-2
docker logs -f tuuuur-kafka-3
```

### Redémarrer tout le cluster

```bash
docker-compose down
docker-compose up -d
```

## 📚 Références

- [KRaft Mode Documentation](https://kafka.apache.org/documentation/#kraft)
- [Replication Documentation](https://kafka.apache.org/documentation/#replication)
- [Confluent Platform Docker Guide](https://docs.confluent.io/kafka-clients/python/current/overview.html)

---

**Créé le**: 14 avril 2026  
**Cluster**: 3 brokers KRaft mode (aucun Zookeeper)
