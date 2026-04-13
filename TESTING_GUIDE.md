# 🧪 Guide de Test – Consumer Groupe avec Partitions

Voici comment tester votre mise en place de Consumer Group avec Partitions.

---

## 📌 Prérequis

Assurez-vous que :

```bash
# 1. Kafka est démarré (docker-compose)
docker-compose -f infrastructure/docker-compose.yml up -d

# 2. Vérifier que le broker Kafka est accessible
docker-compose -f infrastructure/docker-compose.yml logs kafka | grep "started"

# 3. Topic 'orders-events' existe (créé par KafkaTopicInitializer)
docker exec -it tuuuur-kafka kafka-topics --bootstrap-server localhost:9092 --list | grep orders-events
```

---

## 🚀 Test 1 : Démarrage Basique

### Étape 1 : Compiler et démarrer l'application

```bash
# Terminal 1 : Démarrer l'app Quarkus
mvn quarkus:dev

# Output attendu :
# ✅ REBALANCE COMPLÉTÉ : Partitions ASSIGNÉES à cette instance
#    Partitions reçues : [P0, P1, P2]
#    Nombre total : 3
```

### Étape 2 : Vérifier les partitions assignées

```bash
# Terminal 2 : Test API
curl http://localhost:8080/api/partitions/status | jq

# Réponse :
{
  "status": "BALANCED",
  "assignedPartitions": [0, 1, 2],
  "partitionCount": 3,
  "timestamp": 1712973456000,
  "instanceId": "instance-unknown"
}
```

### Étape 3 : Envoyer un ordre de test

```bash
# Terminal 2 : Créer une commande
curl -X POST http://localhost:8080/api/orders/place \
  -H "Content-Type: application/json" \
  -d '{
    "playerId": "player-123",
    "playerName": "TestPlayer",
    "playerRank": "GOLD",
    "itemId": "sword-001",
    "quantity": 1
  }'
```

### Étape 4 : Observer les logs

```bash
# Terminal 1 : Regarder les logs
# Attendu :
# 🎯 ORDER PARTITIONED CONSUMER
#    Topic: orders-events
#    Partition: 0 (ou 1 ou 2 selon le round-robin)
#    Offset: 0
#    OrderId: ord-xxx | Joueur: TestPlayer | Article: Épée Légendaire (x1)
# ✅ Traitement de OrderCreatedEvent [ord-xxx] RÉUSSI
```

---

## 📊 Test 2 : Scalabilité – 3 Instances

### Étape 1 : Lancer 3 instances

```bash
# Terminal 1 : Instance A
PORT=8080 mvn quarkus:dev

# Terminal 2 : Instance B
PORT=8081 mvn quarkus:dev

# Terminal 3 : Instance C
PORT=8082 mvn quarkus:dev
```

### Étape 2 : Observer les logs de rebalancing

```
Instance A :
⚠️  REBALANCE : Partitions RÉVOQUÉES
   Partitions perdues : [P0, P1, P2]
✅ REBALANCE COMPLÉTÉ : Partitions ASSIGNÉES
   Partitions reçues : [P0]

Instance B :
⚠️  REBALANCE : Partitions RÉVOQUÉES
✅ REBALANCE COMPLÉTÉ
   Partitions reçues : [P1]

Instance C :
⚠️  REBALANCE : Partitions RÉVOQUÉES
✅ REBALANCE COMPLÉTÉ
   Partitions reçues : [P2]
```

### Étape 3 : Vérifier la distribution

```bash
# Terminal 4 : Tester chaque instance
curl http://localhost:8080/api/partitions/assigned | jq
# {"assigned": [0], "total": 1}

curl http://localhost:8081/api/partitions/assigned | jq
# {"assigned": [1], "total": 1}

curl http://localhost:8082/api/partitions/assigned | jq
# {"assigned": [2], "total": 1}
```

### Étape 4 : Envoyer 1000 commandes et observer la distribution

```bash
# Terminal 4 : Script de test
for i in {1..1000}; do
  curl -X POST http://localhost:8080/api/orders/place \
    -H "Content-Type: application/json" \
    -d "{
      \"playerId\": \"player-$i\",
      \"playerName\": \"Player$i\",
      \"playerRank\": \"GOLD\",
      \"itemId\": \"sword-001\",
      \"quantity\": 1
    }" 2>/dev/null &

  # Ne pas surcharger
  if [ $((i % 100)) -eq 0 ]; then
    echo "Envoyé $i commandes..."
    sleep 1
  fi
done
```

### Étape 5 : Observer dans les logs

```
Instance A (Partition 0) :
   Offset: 200, Offset: 201, Offset: 202, ...

Instance B (Partition 1) :
   Offset: 230, Offset: 231, Offset: 232, ...

Instance C (Partition 2) :
   Offset: 245, Offset: 246, Offset: 247, ...
```

→ Les messages sont distribués entre les 3 partitions  
→ Chaque instance traite ses propres messages

---

## ⚠️ Test 3 : Failover – Arrêter une Instance

### Étape 1 : Avec 3 instances actives (état précédent)

```bash
# État actuel :
# Instance A : [P0]
# Instance B : [P1]
# Instance C : [P2]
```

### Étape 2 : Arrêter Instance B

```bash
# Terminal 2 : Ctrl+C (arrêter Instance B)
^C

# Résultat dans Terminal 1 (Instance A) et Terminal 3 (Instance C) :
⚠️  REBALANCE : Partitions RÉVOQUÉES
   Partitions perdues : [P0]
✅ REBALANCE COMPLÉTÉ
   Partitions reçues : [P0, P1]  ← Instance A reçoit aussi P1

[Instance C rebalance aussi]
✅ REBALANCE COMPLÉTÉ
   Partitions reçues : [P2]
```

### Étape 3 : Vérifier les assignements

```bash
curl http://localhost:8080/api/partitions/assigned | jq
# {"assigned": [0, 1], "total": 2}  ← Instance A traite maintenant 2 partitions

curl http://localhost:8082/api/partitions/assigned | jq
# {"assigned": [2], "total": 1}
```

### Étape 4 : Envoyer des commands

```bash
curl -X POST http://localhost:8080/api/orders/place ...
# Les commandes arrivent soit sur P0 soit sur P1
# Instance A traite les deux
# Aucune perte de message
```

---

## 🔍 Test 4 : Avec CLI Kafka

### Vérifier l'état du consumer group

```bash
# Terminal 4 : Lister les groups
docker exec -it tuuuur-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --list | grep partitioned

# Output :
# orders-partition-processor-grp
```

### Détails du group

```bash
docker exec -it tuuuur-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orders-partition-processor-grp \
  --describe

# Output :
# GROUP                             TOPIC         PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# orders-partition-processor-grp    orders-events 0          45              45              0
# orders-partition-processor-grp    orders-events 1          38              38              0
# orders-partition-processor-grp    orders-events 2          55              55              0
```

### Réinitialiser les offsets

```bash
# ⚠️ ATTENTION : Cela supprime les offsets
# À faire que en dev/test !

docker exec -it tuuuur-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orders-partition-processor-grp \
  --topic orders-events \
  --reset-offsets \
  --to-earliest \
  --execute

# Ensuite : toutes les instances rejoueront depuis le début
```

---

## 📈 Test 5 : Monitoring via API

### Endpoint de statut

```bash
curl http://localhost:8080/api/partitions/status | jq
```

### Endpoint de santé

```bash
# Utiliser pour Kubernetes liveness probe
curl http://localhost:8080/api/partitions/health | jq

# Réponse (Instance healthy) :
{
  "ready": true,
  "partitions": 2,
  "message": "Instance ready"
}

# Réponse (Instance rebalancing) :
{
  "ready": false,
  "partitions": 0,
  "message": "Instance rebalancing or idle"
}
```

---

## 🐛 Test 6 : Debugging – Voir les Offsets

### Ajouter des offsets personnalisés

```bash
# Voir le dernier offset dans chaque partition
docker exec -it tuuuur-kafka kafka-run-class kafka.tools.GetOffsetShell \
  --bootstrap-server localhost:9092 \
  --topic orders-events

# Output :
# orders-events:0:50
# orders-events:1:48
# orders-events:2:60
#
# Signifie :
# - Partition 0 : 50 messages total
# - Partition 1 : 48 messages total
# - Partition 2 : 60 messages total
```

---

## ✅ Checklist de Test

- [ ] **Démarrage** : App démarre, rebalance effectuée, partitions assignées
- [ ] **Basique** : Envoyer une commande → vue dans les logs avec partition/offset
- [ ] **3 Instances** : Rebalance OK, chaque instance traite 1 partition
- [ ] **Scalabilité** : 1000+ messages → distribution homogène
- [ ] **Failover** : Arrêter Instance → rebalance auto → repartition OK
- [ ] **Recovery** : Redémarrer Instance → rebalance auto → reviens à l'état précédent
- [ ] **API Monitoring** : `/api/partitions/status`, `/health` retournent bon statut
- [ ] **Offsets** : Vérifier LAG = 0 (pas de retard)
- [ ] **Logs** : Voir les ⚠️, ✅, 🎯 dans les logs

---

## 🚀 Pour aller plus loin

### Test : Batch Processing

Modifier `application.properties` :

```properties
mp.messaging.incoming.orders-partitioned-in.max.poll.records=1000
```

Puis tester que l'app peut traiter 1000 messages en batch.

### Test : Session Timeout

Modifier `application.properties` :

```properties
mp.messaging.incoming.orders-partitioned-in.session.timeout.ms=3000
mp.messaging.incoming.orders-partitioned-in.heartbeat.interval.ms=1000
```

Puis :

```bash
# Ajouter un breakpoint dans le consumer
# Laisser dépasser 3 secondes
# Observer les logs : "instance déclarée morte"
# Rebalance auto qui la remplace
```

### Test : DLQ (Dead Letter Queue)

Configurer une DLQ pour les messages rejetés (optionnel, avancé).

---

## 📊 Métriques Clés à Monitorer

| Métrique            | Idéal                            | Alerte                    |
| ------------------- | -------------------------------- | ------------------------- |
| Partitions/Instance | ~N/M (N=partitions, M=instances) | 0 = instance morte        |
| LAG                 | 0 (à jour)                       | >1000 = traitement lent   |
| Rebalance Duration  | <10s                             | >30s = problème réseau    |
| Message Throughput  | Stable                           | Chute = consumer bloqué   |
| Error Count         | 0                                | >10/min = problème métier |
