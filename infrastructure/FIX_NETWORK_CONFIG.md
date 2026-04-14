# 🔧 Fix Network Configuration - Bootstrap Servers

## ❌ Problème détecté

Lors de l'exécution des tests initiaux, des avertissements s'affichaient:

```
WARN Connection to node 2 (localhost/127.0.0.1:9093) could not be established
WARN Connection to node 3 (localhost/127.0.0.1:9094) could not be established
```

Résultats avant la fix:

```
64834 records sent, 12966.8 records/sec, 1222.1 ms avg latency ⚠️ TRÈS ÉLEVÉ
```

---

## 🎯 Cause racine

Les scripts de test s'exécutaient **à l'intérieur du container Kafka-1** via `docker exec`:

```bash
docker exec tuuuur-kafka-1 kafka-producer-perf-test \
  --bootstrap-server localhost:9092,localhost:9093,localhost:9094
```

**Problème:** Dans ce contexte, `localhost` référence le container Kafka-1 lui-même, pas l'hôte machine.

### Architecture réseau

```
┌─────────────────────────────────────────────────────┐
│ Docker compose network (tuuuur-net)                 │
│                                                     │
│  ┌──────────────────┐  ┌──────────────────┐        │
│  │ kafka-broker-1   │  │ kafka-broker-2   │        │
│  │ :29092           │  │ :29092           │        │
│  │ localhost ❌     │  │ (pas accessible) │        │
│  └──────────────────┘  └──────────────────┘        │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

## ✅ Solution appliquée

Modifier les **bootstrap servers** pour utiliser les **noms DNS internes** du réseau Docker:

### Fichiers modifiés

#### perf-test-producer.sh

```bash
# ❌ AVANT
BOOTSTRAP_SERVERS="localhost:9092,localhost:9093,localhost:9094"

# ✅ APRÈS
BOOTSTRAP_SERVERS="kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092"
```

#### perf-test-consumer.sh

```bash
# ❌ AVANT
BOOTSTRAP_SERVERS="localhost:9092,localhost:9093,localhost:9094"

# ✅ APRÈS
BOOTSTRAP_SERVERS="kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092"
```

#### perf-test-full.sh

```bash
# ❌ AVANT
BOOTSTRAP_SERVERS="localhost:9092,localhost:9093,localhost:9094"

# ✅ APRÈS
BOOTSTRAP_SERVERS="kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092"
```

---

## 📊 Résultats avant/après

### Avant la fix

```
64834 records sent, 12966.8 records/sec (12.66 MB/sec), 1222.1 ms avg latency, 2216.0 ms max latency.
[WARN] Connection to node 2 (localhost/127.0.0.1:9093) could not be established
[WARN] Connection to node 3 (localhost/127.0.0.1:9094) could not be established
```

### Après la fix

```
10000 records sent, 17391.304348 records/sec (8.49 MB/sec), 220.68 ms avg latency, 339.00 ms max latency
✅ Pas d'avertissements - Tous les brokers connectés
```

### Métriques comparées

| Métrique                 | Avant  | Après  | Amélioration |
| ------------------------ | ------ | ------ | ------------ |
| **Throughput (msg/sec)** | 12,966 | 17,391 | +34% ⬆️      |
| **Latency average (ms)** | 1,222  | 220    | -82% ⬇️      |
| **Latency max (ms)**     | 2,216  | 339    | -85% ⬇️      |
| **Connection errors**    | YES ❌ | NO ✅  | -100% ✅     |

---

## 🔍 Pourquoi ça marche?

### Les noms DNS internes

Docker Compose crée un réseau bridge nommé `tuuuur-net` avec des services:

- **kafka-broker-1** → Accessible à `kafka-broker-1:29092` depuis d'autres containers
- **kafka-broker-2** → Accessible à `kafka-broker-2:29092` depuis d'autres containers
- **kafka-broker-3** → Accessible à `kafka-broker-3:29092` depuis d'autres containers

Port **29092** = port interne du réseau Docker (pas le port hôte)

### Configuration Kafka

```yaml
# Pour chaque broker dans docker-compose.yml:
KAFKA_LISTENERS: PLAINTEXT://kafka-broker-2:29092,EXTERNAL://0.0.0.0:9092
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-broker-2:29092,EXTERNAL://localhost:9093
```

- **PLAINTEXT**: À utiliser depuis d'autres containers (communication interne)
- **EXTERNAL**: À utiliser depuis l'hôte machine (communication externe)

---

## 📋 Vérification

Pour confirmer que la connectivité fonctionne:

```bash
# Depuis l'hôte machine
docker exec tuuuur-kafka-1 kafka-broker-api-versions \
  --bootstrap-server kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092
```

Résultat attendu:

```
kafka-broker-1:29092 (id: 1 rack: null) -> (...)
kafka-broker-2:29092 (id: 2 rack: null) -> (...)
kafka-broker-3:29092 (id: 3 rack: null) -> (...)
```

---

## 🚀 Impact sur les tests

Après cette fix, vous pouvez maintenant:

✅ Lancer les tests **sans avertissements**  
✅ Obtenir des **métriques fiables** avec tous les brokers engagés  
✅ Disposer d'une **meilleure distribution** des messages entre les brokers  
✅ Profiter d'une **latence stable** et prévisible

---

## 💡 Leçon clé

Quand vous lancez des outils Kafka via `docker exec` dans un container, utilisez **TOUJOURS** les noms DNS internes du réseau Docker, pas `localhost` ou l'IP de l'hôte.

| Usage                     | Bootstrap Servers                                                    |
| ------------------------- | -------------------------------------------------------------------- |
| Depuis l'hôte machine     | `localhost:9092,localhost:9093,localhost:9094`                       |
| **Depuis les containers** | **`kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092`** |
| Production (K8s)          | Utiliser service names du cluster                                    |

---

## ✅ Prochaines étapes

1. ✅ **Fix appliqué** - Bootstrap servers mis à jour
2. ▶ **Tester le consommateur** - `./perf-test-consumer.sh`
3. ▶ **Tester le full** - `./perf-test-full.sh`
4. ▶ **Relancer les tests massifs** avec la nouvelle configuration
