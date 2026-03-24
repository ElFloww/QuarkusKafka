package upjv.insset.analytics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================
 * SalesQueryService – Kafka Streams Interactive Queries
 * ============================================================
 *
 * Les Interactive Queries permettent d'interroger le State Store
 * local de Kafka Streams SANS passer par un topic Kafka.
 * C'est une fonctionnalité puissante pour exposer les résultats
 * d'agrégation directement via une API REST.
 *
 * Le State Store "sales-by-item" est maintenu en mémoire (in-memory)
 * et mis à jour à chaque PaymentSucceededEvent traité par la Topology.
 *
 * Avantage vs lecture du topic analytics-stats :
 *  - Lecture instantanée (pas de consumer lag)
 *  - Résultat courant (valeur agrégée)
 *  - Pas de consommation de ressources Kafka supplémentaires
 * ============================================================
 */
@ApplicationScoped
public class SalesQueryService {

    private static final Logger LOG = Logger.getLogger(SalesQueryService.class);

    // Quarkus injecte automatiquement l'instance KafkaStreams gérée par le framework
    @Inject
    KafkaStreams kafkaStreams;

    /**
     * Retourne toutes les statistiques de ventes (itemId → totalVendu).
     * Utilise les Interactive Queries pour lire le State Store local.
     *
     * @return Map<itemId, totalQuantiteVendue> ou Map vide si le store n'est pas prêt
     */
    public Map<String, Long> getAllSalesStats() {
        try {
            ReadOnlyKeyValueStore<String, Long> store = getStore();
            Map<String, Long> result = new HashMap<>();
            // Itération sur toutes les entrées du State Store
            try (var iterator = store.all()) {
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    result.put(entry.key, entry.value);
                }
            }
            return result;
        } catch (InvalidStateStoreException e) {
            LOG.warnf("[Streams] State Store non disponible (Streams pas encore RUNNING) : %s", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Retourne le total vendu pour un article spécifique.
     *
     * @param itemId Identifiant de l'article (ex: "tshirt-diamant")
     * @return Quantité totale vendue, 0 si aucune vente
     */
    public long getSalesForItem(String itemId) {
        try {
            ReadOnlyKeyValueStore<String, Long> store = getStore();
            Long value = store.get(itemId);
            return value != null ? value : 0L;
        } catch (InvalidStateStoreException e) {
            LOG.warnf("[Streams] State Store non disponible pour item '%s' : %s", itemId, e.getMessage());
            return 0L;
        }
    }

    /**
     * Retourne l'état courant de KafkaStreams.
     */
    public String getStreamsState() {
        return kafkaStreams.state().toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accès au State Store
    // ─────────────────────────────────────────────────────────────────────────

    private ReadOnlyKeyValueStore<String, Long> getStore() {
        return kafkaStreams.store(
                StoreQueryParameters.fromNameAndType(
                        SalesAnalyticsTopology.SALES_STORE_NAME,
                        QueryableStoreTypes.keyValueStore()
                )
        );
    }
}
