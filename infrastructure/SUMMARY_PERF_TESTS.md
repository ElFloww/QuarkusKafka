# 📋 RÉSUMÉ - Tests de Performance Kafka

## 🎯 Ce qui a été créé

Vous disposez maintenant d'une suite complète de tests de performance pour stresser massivement vos brokers Kafka. Voici ce qui a été mis en place:

### 📁 Fichiers créés dans `infrastructure/`

| Fichier                      | Description                           | Usage                                                     |
| ---------------------------- | ------------------------------------- | --------------------------------------------------------- |
| **perf-test-producer.sh**    | Test du producteur Kafka              | `./perf-test-producer.sh [topic] [num_messages] [size]`   |
| **perf-test-consumer.sh**    | Test du consommateur Kafka            | `./perf-test-consumer.sh [topic] [num_messages]`          |
| **perf-test-full.sh**        | Test complet (prod + cons parallèles) | `./perf-test-full.sh [topic] [msgs] [size] [prod] [cons]` |
| **run-perf-tests.sh**        | Menu interactif (recommandé)          | `./run-perf-tests.sh`                                     |
| **monitor-perf-test.sh**     | Monitoring en temps réel              | `./monitor-perf-test.sh [intervalle]`                     |
| **PERF_TESTING_GUIDE.md**    | Guide détaillé (50+ lignes)           | Consultez pour les détails                                |
| **QUICKSTART_PERF_TESTS.md** | Commandes rapides                     | Commandes pré-configurées                                 |

---

## 🚀 Démarrage rapide (3 options)

### Option 1️⃣ : Menu interactif (RECOMMANDÉ)

```bash
cd infrastructure
./run-perf-tests.sh
```

Menu avec 10 options (test léger, modéré, lourd, extrême, personnalisé, etc.)

### Option 2️⃣ : Commande directe simple

```bash
cd infrastructure
./perf-test-producer.sh orders-events 100000 1024
./perf-test-consumer.sh orders-events 100000
```

### Option 3️⃣ : Test complet massif

```bash
cd infrastructure
./perf-test-full.sh orders-events 500000 2048 3 3
```

- 500k messages × 3 producteurs = 1.5M messages
- 2 KB par message = ~3GB de données
- 3 consommateurs parallèles
- ⏱️ Durée: 5-15 minutes

---

## 📊 Architecture des tests

```
                  ┌─────────────────────┐
                  │  run-perf-tests.sh  │
                  │  (Menu interactif)  │
                  └──────────┬──────────┘
                             │
                 ┌───────────┼───────────┐
                 │           │           │
      ┌──────────▼─┐  ┌──────▼──────┐  ┌▼──────────────┐
      │  Producer  │  │  Consumer   │  │  Full Test    │
      │   Test     │  │    Test     │  │  (Prod+Cons)  │
      └────────────┘  └─────────────┘  └───────────────┘
           │               │                    │
           └───────────────┼────────────────────┘
                           │
                      ┌────▼─────┐
                      │  Kafka   │
                      │ Cluster  │
                      │  (3 pts) │
                      └──────────┘
                           │
           ┌───────────────┼────────────────┐
           │               │                │
     ┌─────▼────┐    ┌─────▼────┐    ┌─────▼────┐
     │  Broker  │    │  Broker  │    │  Broker  │
     │    #1    │    │    #2    │    │    #3    │
     │  :9092   │    │  :9093   │    │  :9094   │
     └──────────┘    └──────────┘    └──────────┘
```

---

## 🔍 Profiles de test disponibles

### 🐛 Test léger (DEBUG)

```bash
./run-perf-tests.sh  # Option 1
```

- **Messages**: 10,000
- **Taille**: 512 bytes
- **Total**: ~5MB
- **Durée**: ~10 secondes
- **Utilisation**: Vérifier que tout fonctionne

### ⚙️ Test modéré (STANDARD)

```bash
./run-perf-tests.sh  # Option 2
```

- **Messages**: 100k × 2 producteurs = 200k
- **Taille**: 1KB
- **Total**: ~200MB
- **Durée**: 2-3 minutes
- **Utilisation**: Charge normale

### 💪 Test lourd (STRESS)

```bash
./run-perf-tests.sh  # Option 3
```

- **Messages**: 500k × 3 producteurs = 1.5M
- **Taille**: 2KB
- **Total**: ~3GB
- **Durée**: 5-15 minutes
- **Utilisation**: Stress test significatif

### 🔥 Test extrême (LIMITE)

```bash
./run-perf-tests.sh  # Option 4
```

- **Messages**: 1M × 5 producteurs = 5M
- **Taille**: 1KB
- **Total**: ~5GB
- **Durée**: 20-30 minutes
- **Utilisation**: Trouver les limites du système

---

## 📈 Métriques collectées

Chaque test affiche:

```
2086 records sent, 417.2 records/sec (0.41 MB/sec), 155.3 ms avg latency, 1001.0 ms max latency
^                  ^                                ^                     ^
└ Nombre de msg    └ Throughput (msg/sec)         └ Latence moyenne    └ Latence max
```

| Métrique                 | Bon     | Acceptable | Mauvais  |
| ------------------------ | ------- | ---------- | -------- |
| **Throughput (msg/sec)** | > 50k   | 10k-50k    | < 10k    |
| **Latence moyenne**      | < 50ms  | 50-200ms   | > 200ms  |
| **Latence max**          | < 500ms | 500-1000ms | > 1000ms |
| **Taux erreur**          | 0%      | < 1%       | > 1%     |

---

## 📊 Visualisation des résultats

### 🖥️ Terminal (immédiat)

Les résultats s'affichent directement:

```bash
✅ Les messages ont été envoyés au topic 'orders-events'
```

### 📈 Grafana (détaillé)

Accédez à http://localhost:3000

- **User**: admin
- **Password**: insset
- **Dashboards**: Kafka Dashboard, Tuuuur Dashboard

### 📋 Logs (debug)

```bash
docker logs tuuuur-kafka-1 | tail -100
docker logs tuuuur-kafka-2 | tail -100
docker logs tuuuur-kafka-3 | tail -100
```

---

## 🎯 Cas d'usage typiques

### Cas 1: Vérifier que le nouveau code ne casse rien

```bash
./run-perf-tests.sh  # Option 1 (test léger)
# Comparer les throughput/latency avant/après
```

### Cas 2: Optimiser les performances

```bash
./run-perf-tests.sh  # Option 2 (test standard)
# Modifier les configurations et relancer
# Mesurer l'impact
```

### Cas 3: Trouver les goulets d'étranglement

```bash
./run-perf-tests.sh  # Option 3 (test lourd)
# Watched monitorer-perf-test.sh pour voir où CPU/RAM/Disk explose
```

### Cas 4: Tester la résilience

```bash
# Terminal 1:
./perf-test-full.sh orders-events 500000 1024 3 3

# Terminal 2 (pendant le test):
./monitor-perf-test.sh 2

# Terminal 3 (simuler une panne):
docker stop tuuuur-kafka-2
sleep 30
docker start tuuuur-kafka-2

# → Le test doit continuer sans perdre de messages
```

---

## ⚙️ Configuration avancée

### Modifier les paramètres Kafka

**File**: `perf-test-producer.sh`

```bash
# Changer la compression (ligne 23)
COMPRESSION="lz4"  # ou "gzip", "snappy", "none"

# Changer la taille des batches (ligne 24)
BATCH_SIZE="65536"  # Plus grand = plus de débit, plus de latence

# Changer l'attente (ligne 25)
LINGER_MS="50"  # Moins = moins de latence, moins d'agrégation
```

### Ajouter des topics personnalisés

```bash
cd infrastructure

# Créer un topic
docker exec tuuuur-kafka-1 kafka-topics \
  --bootstrap-server localhost:9092 \
  --create \
  --topic mon-topic \
  --partitions 5 \
  --replication-factor 3

# Tester
./perf-test-producer.sh mon-topic 100000 1024
```

---

## 🛠️ Troubleshooting

### ❌ "Containers not found"

```bash
cd infrastructure
docker compose up -d --build --force-recreate
sleep 30  # Attendre l'init
```

### ❌ "Latency extrêmement haute"

- Réduisez `NUM_PRODUCERS` et `NUM_CONSUMERS` dans `perf-test-full.sh`
- Vérifiez: `docker stats tuuuur-kafka-1`
- Vérifiez l'espace disque: `df -h`

### ❌ "Test s'arrête brutalement"

```bash
docker logs tuuuur-kafka-1 | grep -i "error\|exception" | tail -20
```

### ❌ "Topic non trouvé"

Le script le crée automatiquement. Si erreur:

```bash
docker exec tuuuur-kafka-1 kafka-topics \
  --bootstrap-server localhost:9092 \
  --list
```

---

## 📚 Documentation complète

Consultez ces fichiers pour plus de détails:

1. **[QUICKSTART_PERF_TESTS.md](QUICKSTART_PERF_TESTS.md)** - Commandes rapides (👈 LISEZ MOI EN PREMIER)
2. **[PERF_TESTING_GUIDE.md](PERF_TESTING_GUIDE.md)** - Guide détaillé complet (50+ pages)
3. **[README_PARTITIONS.md](README_PARTITIONS.md)** - Gestion des partitions
4. **[CONSUMER_GROUP_GUIDE.md](CONSUMER_GROUP_GUIDE.md)** - Gestion des groupes de consommateurs
5. **[REPLICATION_SETUP.md](infrastructure/REPLICATION_SETUP.md)** - Architecture KRaft

---

## ✅ Check-list avant de lancer

- [ ] Docker est lancé
- [ ] `docker compose ps` montre 3 brokers actifs
- [ ] Vous avez 4GB RAM minimum libres
- [ ] Vous avez 20GB disque disponible
- [ ] Port 3000 (Grafana) n'est pas utilisé
- [ ] Ports 9092, 9093, 9094 (Kafka) ne sont pas utilisés

---

## 📞 Support

Si vous avez des questions:

1. 📖 Lisez **QUICKSTART_PERF_TESTS.md** en premier
2. 📚 Consultez **PERF_TESTING_GUIDE.md** pour les détails
3. 🔍 Vérifiez les logs: `docker logs tuuuur-kafka-1`
4. 📊 Consultez Grafana: http://localhost:3000

---

## 🎓 Concepts clés

- **Topic**: Queue distribuée où les messages sont publiés
- **Partition**: Unité de parallélisme et stockage
- **Broker**: Serveur Kafka (vous en avez 3)
- **Producer**: Client qui envoie des messages
- **Consumer**: Client qui lit des messages
- **ConsumerGroup**: Groupe de consommateurs qui se partagent les partitions
- **Replication Factor**: Nombre de copies de chaque message (vous avez 3)
- **Throughput**: Nombre de messages/sec qu'on peut traiter
- **Latency**: Temps entre envoyer et recevoir un message

---

## 🚀 Prochaines étapes

1. ✅ Lancer un test léger pour valider: `./run-perf-tests.sh` → Option 1
2. 📈 Lancer un test standard: `./run-perf-tests.sh` → Option 2
3. 📊 Observer dans Grafana: http://localhost:3000
4. 🔥 Tester la résilience: Arrêter/redémarrer un broker pendant le test
5. 🎯 Optimiser: Modifier les paramètres et re-tester

---

**Bon test! 🚀**
