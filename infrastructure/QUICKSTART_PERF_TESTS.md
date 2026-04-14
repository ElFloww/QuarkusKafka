# 🚀 Quick Start - Kafka Performance Testing

## ⚡ Commandes rapides

### 1️⃣ Lancer l'interface interactive (recommandé)

```bash
cd infrastructure
./run-perf-tests.sh
```

Puis sélectionnez une option du menu.

---

## 📋 Tests pré-configurés

### Test léger (10 secondes)

```bash
cd infrastructure
./perf-test-producer.sh orders-events 10000 512
./perf-test-consumer.sh orders-events 10000
```

### Test modéré (2-3 minutes)

```bash
cd infrastructure
./perf-test-full.sh orders-events 100000 1024 2 2
```

### Test lourd (5-15 minutes)

```bash
cd infrastructure
./perf-test-full.sh orders-events 500000 2048 3 3
```

### Test extrême (20-30 minutes)

```bash
cd infrastructure
./perf-test-full.sh orders-events 1000000 1024 5 5
```

---

## 🔧 Tests personnalisés

### Producteur seul (1M messages)

```bash
cd infrastructure
./perf-test-producer.sh rank-events 1000000 1024
```

### Consommateur seul

```bash
cd infrastructure
./perf-test-consumer.sh orders-events 500000
```

### Test personnalisé complet

```bash
cd infrastructure
./perf-test-full.sh stock-events 200000 2048 2 3
#                   ^^^^^^^^^^^^^ ^^^^^^ ^^^^ ^ ^
#                   topic        messages size prod cons
```

---

## 📊 Monitoring en parallèle

Ouvrez un AUTRE terminal et lancez:

```bash
cd infrastructure
./monitor-perf-test.sh 2    # Rafraîchissement tous les 2s
```

Cela affiche:

- ✅ État des brokers Kafka
- 💻 Utilisation CPU/RAM/Disk
- 📚 Informations des topics
- 🖥️ État du système

---

## 🎯 Scénarios d'utilisation

### Scénario: Test complet + monitoring

```bash
# Terminal 1: Lancer le test
cd infrastructure
./perf-test-full.sh orders-events 500000 1024 3 2

# Terminal 2: Live monitoring
cd infrastructure
./monitor-perf-test.sh 2
```

### Scénario: Tester la résilience

```bash
# Terminal 1: Lancer le test
cd infrastructure
./perf-test-full.sh orders-events 500000 1024 3 3 &

# Terminal 2: Arrêter un broker via le terminal
# (pendant que le test s'exécute)
docker stop tuuuur-kafka-2

# Le cluster doit continuer à fonctionner avec 2/3 brokers!
sleep 30

# Redémarrer le broker
docker start tuuuur-kafka-2

# Le test doit se terminer normalement
```

### Scénario: Tester plusieurs topics

```bash
cd infrastructure

# Lancer 3 tests producteurs en parallèle
./perf-test-producer.sh orders-events 200000 1024 &
./perf-test-producer.sh rank-events 200000 1024 &
./perf-test-producer.sh stock-events 200000 1024 &

# Attendre que tous les producteurs terminent
wait

# Puis lancer les consommateurs
./perf-test-consumer.sh orders-events 200000 &
./perf-test-consumer.sh rank-events 200000 &
./perf-test-consumer.sh stock-events 200000 &

# Attendre que tous les consommateurs terminent
wait
```

---

## ✅ Checklist avant de tester

- [ ] Les 3 brokers Kafka sont actifs: `docker ps | grep tuuuur-kafka`
- [ ] Le cluster est sain: `docker compose ps`
- [ ] Grafana est accessible: http://localhost:3000
- [ ] Les topics existent ou seront créés automatiquement
- [ ] Vous avez suffisamment d'espace disque (~10-20GB recommandé)
- [ ] Vous avez 4-8GB de RAM disponible

---

## 📊 Où voir les résultats

### 1️⃣ Terminal (résumé rapide)

Le script affiche les métriques directement:

```
2086 records sent, 417.2 records/sec (0.41 MB/sec), 155.3 ms avg latency, 1001.0 ms max latency
```

### 2️⃣ Grafana (graphiques détaillés)

```
URL: http://localhost:3000
User: admin
Password: insset
```

Dashboards:

- **Kafka Dashboard**: Métriques des brokers
- **Tuuuur Dashboard**: Métriques applicatives

### 3️⃣ Logs des brokers

```bash
docker logs tuuuur-kafka-1 | tail -100
docker logs tuuuur-kafka-2 | tail -100
docker logs tuuuur-kafka-3 | tail -100
```

---

## 🔗 Liens utiles

- [PERF_TESTING_GUIDE.md](PERF_TESTING_GUIDE.md) - Guide détaillé complet
- [README_PARTITIONS.md](README_PARTITIONS.md) - Gestion des partitions
- [CONSUMER_GROUP_GUIDE.md](CONSUMER_GROUP_GUIDE.md) - Gestion des groupes

---

## ⚠️ Troubleshooting rapide

| Problème                    | Solution                                          |
| --------------------------- | ------------------------------------------------- |
| "Containers not found"      | `docker compose up -d` dans `infrastructure/`     |
| "Latency > 1000ms"          | Réduisez les producteurs/consommateurs parallèles |
| "Test s'arrête brutalement" | Vérifiez `docker logs tuuuur-kafka-1`             |
| "Pas de messages"           | Attendez 30s après le démarrage du cluster        |
| "Topics n'existent pas"     | Les scripts les créent automatiquement            |

---

**Besoin d'aide ?**
Consultez [PERF_TESTING_GUIDE.md](PERF_TESTING_GUIDE.md) pour plus de détails.
