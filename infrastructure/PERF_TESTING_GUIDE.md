# 🚀 Kafka Performance Testing Guide - Tests Massifs

Ce guide explique comment utiliser les scripts de test de performance Kafka pour stresser vos brokers et mesurer les performances.

## 📋 Vue d'ensemble

Vous disposez de 3 scripts pour tester les performances :

| Script                  | Description                                           | Usage                                                           |
| ----------------------- | ----------------------------------------------------- | --------------------------------------------------------------- |
| `perf-test-producer.sh` | Test du producteur avec volume massif                 | `./perf-test-producer.sh [topic] [num_messages] [message_size]` |
| `perf-test-consumer.sh` | Test du consommateur en lecture                       | `./perf-test-consumer.sh [topic] [num_messages]`                |
| `perf-test-full.sh`     | Test complet (producteurs + consommateurs parallèles) | `./perf-test-full.sh [topic] [num_msgs] [size] [prod] [cons]`   |

## 🎯 Avant de commencer

Assurez-vous que votre cluster Kafka est en cours d'exécution:

```bash
cd infrastructure
docker compose up -d --build --force-recreate
```

Vérifiez que les 3 brokers sont actifs:

```bash
docker ps | grep tuuuur-kafka
```

Vous devriez voir:

- `tuuuur-kafka-1` sur le port 9092
- `tuuuur-kafka-2` sur le port 9093
- `tuuuur-kafka-3` sur le port 9094

## 📊 Configurations recommandées

### 1️⃣ Test léger (debug/validation)

```bash
./perf-test-producer.sh orders-events 10000 512
```

- 10k messages de 512 bytes
- Temps estimé: ~10 secondes
- Utile pour vérifier que tout fonctionne

### 2️⃣ Test modéré (charge standard)

```bash
./perf-test-full.sh orders-events 100000 1024 2 2
```

- 100k messages x 2 producteurs = 200k messages totaux
- 1KB par message = ~200MB de données
- 2 consommateurs parallèles
- Temps estimé: 2-3 minutes

### 3️⃣ Test lourd (stress massif)

```bash
./perf-test-full.sh orders-events 500000 2048 3 3
```

- 500k messages x 3 producteurs = 1.5M messages totaux
- 2KB par message = ~3GB de données
- 3 consommateurs parallèles
- Temps estimé: 5-10 minutes
- ⚠️ C'est du stress test intensif!

### 4️⃣ Test extrême (limite)

```bash
./perf-test-full.sh orders-events 1000000 1024 5 5
```

- 1M messages x 5 producteurs = 5M messages
- 1KB par message = ~5GB de données
- 5 consommateurs parallèles
- Temps estimé: 15-20 minutes
- ⚠️ Peut causer des crashes si infrastructure insuffisante

## 🔧 Paramètres détaillés

### Producteur

**Syntaxe**: `./perf-test-producer.sh [topic] [num_messages] [message_size]`

| Paramètre      | Défaut          | Description                       |
| -------------- | --------------- | --------------------------------- |
| `topic`        | `orders-events` | Topic Kafka cible                 |
| `num_messages` | `500000`        | Nombre de messages à produire     |
| `message_size` | `1024`          | Taille de chaque message en bytes |

**Exemple**:

```bash
./perf-test-producer.sh orders-events 1000000 2048
```

Le producteur utilise ces optimisations:

- **Compression**: Snappy (réduit la bande passante de ~50%)
- **Batch size**: 32KB (améliore le throughput)
- **Linger MS**: 100ms (permet de grouper les requêtes)
- **Acks**: ALL (garantit la réplication sur tous les brokers)

### Consommateur

**Syntaxe**: `./perf-test-consumer.sh [topic] [num_messages]`

| Paramètre      | Défaut          | Description               |
| -------------- | --------------- | ------------------------- |
| `topic`        | `orders-events` | Topic Kafka à consommer   |
| `num_messages` | `500000`        | Nombre de messages à lire |

**Exemple**:

```bash
./perf-test-consumer.sh orders-events 1000000
```

Le consommateur:

- Crée un groupe unique pour chaque test (avec timestamp)
- Lit depuis le début du topic (earliest)
- Affiche les statistiques de performance en détail

### Test Complet

**Syntaxe**: `./perf-test-full.sh [topic] [num_messages] [message_size] [num_producers] [num_consumers]`

| Paramètre       | Défaut          | Description                        |
| --------------- | --------------- | ---------------------------------- |
| `topic`         | `orders-events` | Topic Kafka cible                  |
| `num_messages`  | `500000`        | Nombre de messages par producteur  |
| `message_size`  | `1024`          | Taille de chaque message en bytes  |
| `num_producers` | `3`             | Nombre de producteurs parallèles   |
| `num_consumers` | `2`             | Nombre de consommateurs parallèles |

**Exemple**:

```bash
./perf-test-full.sh rank-events 200000 1024 3 2
```

Cela lancera:

- 3 producteurs en parallèle (600k messages au total)
- 2 consommateurs qui liront tous les 600k messages
- Affichera les performances de chaque phase

## 📈 Métriques clés à observer

Après chaque test, vous verrez des métriques comme:

```
2086 records sent, 417.2 records/sec (0.41 MB/sec), 155.3 ms avg latency, 1001.0 ms max latency
```

| Métrique      | Signification                                        |
| ------------- | ---------------------------------------------------- |
| `records/sec` | Throughput - plus c'est haut, mieux c'est            |
| `MB/sec`      | Bande passante consommée                             |
| `avg latency` | Latence moyenne - importante pour l'utilisateur      |
| `max latency` | Latence maximale - peut indiquer des problèmes GC/IO |

## 🎯 Scénarios de test

### Scénario 1: Contention en écriture

```bash
# Beaucoup d'écritures, peu de lectures
./perf-test-full.sh orders-events 500000 1024 5 1
```

### Scénario 2: Contention en lecture

```bash
# Peu d'écritures, beaucoup de lectures
./perf-test-full.sh orders-events 500000 1024 1 5
```

### Scénario 3: Test de rééquilibrage

```bash
# Pendant que le test s'exécute:
./perf-test-full.sh orders-events 500000 1024 3 3

# Dans un autre terminal, arrêtez/redémarrez un broker:
docker stop tuuuur-kafka-2
sleep 30
docker start tuuuur-kafka-2
```

Observe comment le cluster gère le rééquilibrage.

### Scénario 4: Test en cascade multi-topic

```bash
# Test sur plusieurs topics en même temps
./perf-test-producer.sh orders-events 200000 1024 &
./perf-test-producer.sh rank-events 200000 1024 &
./perf-test-producer.sh stock-events 200000 1024 &
wait  # Attendre tous les producteurs
```

## 🔍 Diagnostic et troubleshooting

### Le test ne démarre pas

```bash
# Vérifier que Kafka est actif
docker exec tuuuur-kafka-1 kafka-broker-api-versions --bootstrap-server localhost:9092

# S'il ne répond pas, attendre 30 secondes et réessayer
docker compose logs tuuuur-kafka-1 | tail -50
```

### Latence très élevée (>1000ms)

- Réduisez le nombre de producteurs/consommateurs parallèles
- Réduisez la taille des messages
- Vérifiez les ressources Docker: `docker stats`

### Test s'arrête brutalement

```bash
# Vérifier les logs des brokers
docker logs tuuuur-kafka-1 | tail -100
docker logs tuuuur-kafka-2 | tail -100
docker logs tuuuur-kafka-3 | tail -100
```

### Messages perdus après test

Vérifiez que le topic a bien `replication-factor=3`:

```bash
docker exec tuuuur-kafka-1 kafka-topics --bootstrap-server localhost:9092 --describe --topic orders-events
```

## 📊 Monitoring pendant le test

Lancez Grafana à côté pour voir les métriques:

```
URL: http://localhost:3000
User: admin
Password: insset
```

Les dashboards disponibles:

- **Kafka Dashboard**: Métriques des brokers en temps réel
- **Tuuuur Dashboard**: Métriques applicatives

## 💡 Tips de performance

1. **Compression**: Active par défaut (Snappy). Pour tester sans:
   - Modifier la ligne `compression.type="$COMPRESSION"` dans le script

2. **Topics multi-partition**: Les tests utilisent 3 partitions par défaut
   - Cela permet le parallélisme des producteurs/consommateurs

3. **Replication factor**: Configuré à 3 pour la résilience
   - Garantit qu'aucun message n'est perdu même si un broker tombe

4. **Batch size**: 32KB par défaut
   - Augmentez à 65536 pour plus de throughput (mais plus de latence)

## 🚨 Limitations

- Les tests utilisent le port PLAINTEXT (pas de sécurité)
- Les messages sont du texte brut (optimisé pour la taille)
- Aucune authentification Kerberos/mTLS
- Les tests sont bloquants (CLI, pas d'interface graphique)

## 📝 Exemple complet

```bash
#!/bin/bash
cd ~/Documents/M2/Quarkus/Kafka/infrastructure

echo "🚀 Démarrage du cluster Kafka..."
docker compose up -d --build

echo "⏳ Attente que les brokers soient prêts..."
sleep 30

echo "📊 Lancement du test de performance massif..."
./perf-test-full.sh orders-events 500000 1024 3 2

echo "✅ Test terminé! Consultez Grafana: http://localhost:3000"
```

Lancez-le avec:

```bash
bash run-test.sh
```

---

**Besoin d'aide ?**

- Consultez les logs: `docker logs tuuuur-kafka-1`
- Vérifiez l'état du cluster: `docker compose ps`
- Regardez les métriques Grafana en temps réel
