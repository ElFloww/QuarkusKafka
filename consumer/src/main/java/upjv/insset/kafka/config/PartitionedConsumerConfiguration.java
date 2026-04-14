package upjv.insset.kafka.config;

import io.smallrye.reactive.messaging.kafka.KafkaConnectorIncomingConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import upjv.insset.kafka.services.PartitionRebalanceListener;

/**
 * ============================================================
 * PartitionedConsumerConfiguration – Wiring du RebalanceListener
 * ============================================================
 *
 * Responsabilité :
 *  Configurer le Consumer Kafka pour utiliser le PartitionRebalanceListener.
 *  Ce singleton enregistre les callbacks de rebalancing.
 *
 * Concepts démontrés :
 *  - CooperativeStickyAssignor : stratégie de rebalancing minimisant
 *                                la disruption (vs RoundRobin qui reassigne tout)
 *  - Rebalance Listener        : hooks pour traiter les changements
 *                                d'assignement des partitions
 *
 * Configuration dans application.properties :
 *  mp.messaging.incoming.orders-partitioned-in.partition.assignment.strategy
 *    = CooperativeStickyAssignor (par défaut dans Kafka 3.1+)
 *
 * ============================================================
 */
@ApplicationScoped
public class PartitionedConsumerConfiguration {

    private static final Logger LOG = Logger.getLogger(PartitionedConsumerConfiguration.class);

    @Inject
    PartitionRebalanceListener rebalanceListener;

    @ConfigProperty(name = "mp.messaging.incoming.orders-partitioned-in.group.id",
                   defaultValue = "orders-partition-processor-grp")
    String groupId;

    @ConfigProperty(name = "kafka.bootstrap.servers",
                   defaultValue = "localhost:9092")
    String bootstrapServers;

    /**
     * Initialiser et configurer le ConsumerConfig avec le rebalance listener.
     * Appelé au démarrage de l'appli (par Quarkus).
     */
    public void init() {
        LOG.infof(
            "🔧 Initialisation PartitionedConsumer\n" +
            "   Group ID: %s\n" +
            "   Bootstrap Servers: %s\n" +
            "   Rebalance Listener: %s",
            groupId, bootstrapServers,
            rebalanceListener.getClass().getSimpleName()
        );

        // 📌 NOTE : Le PartitionRebalanceListener est enregistré via SmallRye.
        //          SmallRye détecte automatiquement les implémentations de
        //          KafkaConsumerRebalanceListener dans le CDI context
        //          si la propriété est activée :
        //
        //          mp.messaging.incoming.orders-partitioned-in
        //              .consumer-rebalance-listener.enabled=true
        //
        // ✅ Aucun enregistrement manuel nécessaire en Quarkus 3.9+
        
        LOG.infof("✅ PartitionedConsumerConfiguration prête");
    }

    /**
     * Configuration recommandée pour les partitions.
     * 📋 Référence pour les application.properties :
     *
     * # Stratégie d'assignement des partitions
     * mp.messaging.incoming.orders-partitioned-in.partition.assignment.strategy=CooperativeStickyAssignor
     *   → Sticky = partitions restent assignées aux mêmes instances autant que possible
     *   → Cooperative = rebalances moins disruptivos
     *
     * # Isolation du consumer group
     * mp.messaging.incoming.orders-partitioned-in.isolation.level=read_committed
     *   → Lire seulement les messages "committed" (ex: après transactions Kafka)
     *   → Alternative: read_uncommitted (plus rapide, moins safe)
     *
     * # Gestion des offsets
     * mp.messaging.incoming.orders-partitioned-in.enable.auto.commit=true
     *   → Commiter auto les offsets chaque N ms (voir auto.commit.interval.ms)
     *   → Alternative: false (commit manuel via message.ack())
     *
     * # Fetch sizes
     * mp.messaging.incoming.orders-partitioned-in.fetch.min.bytes=1024
     *   → Attendre au minimum 1KB avant de retourner une réponse au consumer
     *   → Augmenter → moins de requêtes, latence plus haute
     *
     * mp.messaging.incoming.orders-partitioned-in.fetch.max.wait.ms=500
     *   → Timeout maxi pour batching les messages (ms)
     *   → Si 500ms écoulée, retourner même si < 1KB reçu
     */

    /**
     * 🎯 Checklist de configuration pour la production :
     *
     * REBALANCING :
     * ☑ session.timeout.ms     = 30000 (détection rapide des instances mortes)
     * ☑ heartbeat.interval.ms  = 10000 (ping fréquent)
     * ☑ max.poll.interval.ms   = 300000 (si traitement peut durer longtemps)
     * ☑ partition.assignment.strategy = CooperativeStickyAssignor
     *
     * PERFORMANCES :
     * ☑ fetch.min.bytes        = 1024 (batching)
     * ☑ fetch.max.wait.ms      = 500
     * ☑ max.poll.records       = 100 ou adapté à votre charge
     *
     * RELIABILITY :
     * ☑ enable.auto.commit     = false (commit manuel pour atomicité)
     * ☑ auto.offset.reset      = earliest (rejeu en cas de problème)
     * ☑ isolation.level        = read_committed (si transactions)
     *
     * MONITORING :
     * ☑ PartitionRebalanceListener implémenté
     * ☑ PartitionMonitoringResource exposé pour metrics
     * ☑ Logs Quarkus en DEBUG
     * ☑ Prometheus/Grafana pour alertes LAG
     *
     */
}
