package upjv.insset.kafka.consumers;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import upjv.insset.kafka.events.OrderCreatedEvent;

import java.util.concurrent.CompletionStage;

/**
 * ============================================================
 * OrderPartitionedConsumer – Gestion des Partitions
 * ============================================================
 *
 * Responsabilité :
 *  Démontrer comment les partitions Kafka sont distribuées entre
 *  les instances d'un Consumer Group. Chaque instance traite
 *  une ou plusieurs partitions de façon exclusive.
 *
 * Concepts Kafka démontrés :
 *  - Consumer Group (group.id)     : ensemble d'instances consommant
 *                                    ensemble du même topic.
 *                                    Kafka assigne les partitions entre elles.
 *  - KafkaMessage<T>               : accès aux metadata Kafka
 *                                    (partition, offset, timestamp, etc).
 *  - Rebalancing                   : quand une instance monte/descend,
 *                                    Kafka réassigne les partitions.
 *  - Auto-commit vs Manual Commit  : par défaut auto, mais ici on utilise
 *                                    ack() pour un contrôle explicite.
 *
 * Exemple de déploiement horizontal :
 *  • Si topic orders-events a 3 partitions (P0, P1, P2)
 *  • Deployer 3 instances du service → chacune traite 1 partition
 *  • Si 1 instance tombe → Kafka réassigne ses partitions aux 2 restantes
 *  • Rebalancing automatic = haute disponibilité
 *
 * Configuration dans application.properties :
 *  ✓ group.id=orders-partition-processor-grp : toutes les instances
 *                                             du groupe partagent cet ID
 *  ✓ max.poll.records=100                    : batch size (nombre de
 *                                             messages à traiter par appel)
 *  ✓ session.timeout.ms=30000                : perte du consommateur
 *                                             détectée après 30s d'inactivité
 *  ✓ heartbeat.interval.ms=10000             : "ping" au broker tous les 10s
 * ============================================================
 */
@ApplicationScoped
public class OrderPartitionedConsumer {

    private static final Logger LOG = Logger.getLogger(OrderPartitionedConsumer.class);

    /**
     * Consomme les OrderCreatedEvents depuis le topic 'orders-events'.
     *
     * Chaque instance de ce service (si repliquée) recevra une ou plusieurs
     * partitions du topic. La distribution est gérée automatiquement par Kafka.
     *
     * @param message Enveloppe Kafka avec accès à :
     *                - message.getPayload()        : l'événement OrderCreatedEvent
     *                - message.getMetadata()       : partition, offset, timestamp, etc
     *                - message.ack()               : valider le traitement
     *                - message.nack(e)             : signaler une erreur
     */
    @Incoming("orders-partitioned-in")
    @Blocking
    public CompletionStage<Void> processOrderWithPartitionTracking(
            Message<OrderCreatedEvent> message) {

        // Extraire le payload (l'événement métier)
        OrderCreatedEvent event = message.getPayload();

        LOG.infof(
            "🎯 ORDER PARTITIONED CONSUMER (Consumer Group: orders-partition-processor-grp)\n" +
            "   OrderId: %s\n" +
            "   Joueur: %s (Rang: %s)\n" +
            "   Article: %s (x%d) @ %.2f€/pcs\n" +
            "   Total: %.2f€",
            event.orderId, event.playerName, event.playerRank, 
            event.itemName, event.quantity, event.unitPrice,
            event.unitPrice * event.quantity
        );

        try {
            // ── Logique métier ──────────────────────────────────────
            // En pratique, vous pourriez :
            //  1. Écrire en base de données (MySQL, Postgres)
            //  2. Mettre en cache (Redis)
            //  3. Appeler un service externe
            //  4. Enrichir avec des données tierces
            // ─────────────────────────────────────────────────────────

            // Pour cette démo, on log simplement
            LOG.infof("✅ Traitement de OrderCreatedEvent [%s] RÉUSSI", event.orderId);

            // Valider explicitement l'offset
            // Cela signale à Kafka qu'on a consommé le message avec succès
            return message.ack();

        } catch (Exception e) {
            LOG.errorf(
                "❌ ERREUR traitement OrderCreatedEvent [%s] : %s",
                event.orderId, e.getMessage());
            e.printStackTrace();

            // Signaler l'erreur (DLQ si configurée, ou rejeu après retry)
            return message.nack(e);
        }
    }

    /**
     * ─────────────────────────────────────────────────────────────────────────
     * 📚 Concepts clés pour la scalabilité horizontale :
     * ─────────────────────────────────────────────────────────────────────────
     *
     * 1️⃣ CONSUMER GROUP (group.id)
     *    • Identifie un ensemble d'instances consommant ensemble
     *    • Ex: "orders-partition-processor-grp"
     *    • Toutes les instances du groupe partagent le même ID
     *    • Chaque groupe consomme indépendamment du topic
     *
     * 2️⃣ PARTITIONS ET DISTRIBUTION
     *    • Un topic est divisé en N partitions (ex: 3 partitions)
     *    • Kafka assigne les partitions aux instances de façon exclusive
     *      - 1 instance   → traite 3 partitions
     *      - 3 instances  → chacune traite 1 partition
     *      - 5 instances  → 3 d'entre elles traitent 1 partition,
     *                       2 restent inactives
     *    • Objectif : paralléliser le traitement des messages
     *
     * 3️⃣ REBALANCING
     *    • Quand une instance démarre ou s'arrête, Kafka réassigne les partitions
     *    • durée : quelques secondes
     *    • Impacts :
     *      ✓ Haute disponibilité : récupération automatique en cas de panne
     *      ✗ Pause momentanée de traitement : le groupe s'arrête le temps du rebalance
     *    • Paramètres :
     *      - session.timeout.ms   : après combien d'inactivité
     *                               une instance est déclarée morte (30s par défaut)
     *      - heartbeat.interval.ms : fréquence des pings (10s par défaut)
     *
     * 4️⃣ OFFSET MANAGEMENT
     *    • Kafka retient où chaque groupe en est dans le topic
     *    • Ce offset est persisté dans Kafka (topic __consumer_offsets)
     *    • Si l'instance redémarre, elle reprend depuis le dernier offset ACKé
     *    • Modes :
     *      - auto.offset.reset=earliest  : commencer depuis le début si absence d'offset
     *      - auto.offset.reset=latest    : commencer depuis la fin
     *
     * 5️⃣ SCALING HORIZONTALEMENT
     *    • Créer N répliques du pod/conteneur avec même group.id
     *    • Kubernetes/Docker gère la scalabilité en startup/shutdown
     *    • À chaque changement, rebalancing automatique
     *    • Gestion du state ?
     *      ✓ Stateless → scalable facilement (redis/db externe pour state)
     *      ✗ Stateful → complexe (state stores Kafka Streams, etc)
     * ─────────────────────────────────────────────────────────────────────────
     */
}
