package upjv.insset.kafka.services;

import io.smallrye.reactive.messaging.kafka.KafkaConsumerRebalanceListener;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ============================================================
 * PartitionRebalanceListener – Monitorer les Rebalances
 * ============================================================
 *
 * Responsabilité :
 *  Implémenter les hooks de Rebalancing pour loguer et monitorer
 *  l'assignement des partitions à cette instance.
 *
 * Cas d'usage :
 *  ✓ Logging/Debugging : voir quelles partitions sont assignées
 *  ✓ Métriques         : publier le nombre de partitions assignées
 *  ✓ State Management  : initialiser du state local par partition
 *  ✓ Graceful Shutdown : cleanup avant de perdre les partitions
 *
 * Concepts démontrés :
 *  - onPartitionsRevoked()   : appelé AVANT la perte des partitions
 *                              (opportunité pour cleanup/sauvegarder état)
 *  - onPartitionsAssigned()  : appelé APRÈS la réception des partitions
 *                              (opportunité pour initialiser état local)
 * ============================================================
 */
@ApplicationScoped
public class PartitionRebalanceListener implements KafkaConsumerRebalanceListener {

    private static final Logger LOG = Logger.getLogger(PartitionRebalanceListener.class);

    // État local : partitions actuellement assignées
    private volatile Set<Integer> assignedPartitions = new HashSet<>();

    /**
     * Appelé AVANT que cette instance perde ses partitions.
     * C'est le moment pour :
     *   - Commiter les offsets en attente
     *   - Persister l'état local (state stores)
     *   - Arrêter les tâches associées
     *   - Logger/Notifier les alertes
     */
    @Override
    public void onPartitionsRevoked(Consumer<?, ?> consumer,
                                     Collection<TopicPartition> partitions) {
        String partitionStr = partitions.stream()
                .map(tp -> "P" + tp.partition())
                .collect(Collectors.joining(", "));

        LOG.warnf(
            "⚠️  REBALANCE : Partitions RÉVOQUÉES [instance perdue les partitions]\n" +
            "   Partitions perdues : [%s]\n" +
            "   Raison : Probablement une nouvelle instance s'ajoute au groupe,\n" +
            "            ou cette instance s'arrête.",
            partitionStr
        );

        try {
            // Optionnel : commiter les offsets manuellement
            consumer.commitSync();
            LOG.infof("✅ Offsets validés avant perte des partitions");
        } catch (Exception e) {
            LOG.errorf("❌ Erreur commit des offsets : %s", e.getMessage());
        }

        // Mettre à jour l'état interne
        assignedPartitions.removeAll(
            partitions.stream()
                .map(TopicPartition::partition)
                .collect(Collectors.toSet())
        );

        LOG.infof("✅ Cleanup terminé pour partitions : [%s]", partitionStr);
    }

    /**
     * Appelé APRÈS que cette instance reçoive ses partitions.
     * C'est le moment pour :
     *   - Initialiser l'état local (caches, buffers)
     *   - Restaurer l'état depuis une source externe
     *   - Démarrer des tâches associées
     *   - Log de diagnostic
     */
    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer,
                                      Collection<TopicPartition> partitions) {
        String partitionStr = partitions.stream()
                .map(tp -> "P" + tp.partition())
                .collect(Collectors.joining(", "));

        LOG.infof(
            "✅ REBALANCE COMPLÉTÉ : Partitions ASSIGNÉES à cette instance\n" +
            "   Partitions reçues : [%s]\n" +
            "   Nombre total : %d\n" +
            "   Cette instance va traiter les messages de ces partitions de façon EXCLUSIVE",
            partitionStr, partitions.size()
        );

        // Log les offsets actuels
        partitions.forEach(tp -> {
            try {
                long offset = consumer.position(tp);
                LOG.debugf("   Topics[%s], Partition[%d] → Position actuelle: %d",
                           tp.topic(), tp.partition(), offset);
            } catch (Exception e) {
                LOG.debugf("   Topics[%s], Partition[%d] → Erreur lecture position: %s",
                           tp.topic(), tp.partition(), e.getMessage());
            }
        });

        // Mettre à jour l'état interne
        assignedPartitions.addAll(
            partitions.stream()
                .map(TopicPartition::partition)
                .collect(Collectors.toSet())
        );

        LOG.infof("📊 Partitions maintenant traitées par cette instance : %s", assignedPartitions);
    }

    /**
     * Retourner les partitions actuellement assignées à cette instance.
     * Utile pour les métriques ou le debugging.
     *
     * @return Set immutable des numéros de partitions
     */
    public Set<Integer> getAssignedPartitions() {
        return Set.copyOf(assignedPartitions);
    }

    /**
     * Retourner le nombre de partitions assignées.
     * Utile pour Micrometer/Prometheus.
     *
     * @return nombre de partitions
     */
    public int getPartitionCount() {
        return assignedPartitions.size();
    }
}
