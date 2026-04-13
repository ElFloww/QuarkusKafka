package upjv.insset.kafka.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.Stores;
import org.jboss.logging.Logger;
import upjv.insset.kafka.events.PaymentSucceededEvent;

/**
 * ============================================================
 * SalesAnalyticsTopology – Kafka Streams API démonstration
 * ============================================================
 *
 * Cette classe construit la Topology Kafka Streams qui calcule
 * en temps réel le nombre de t-shirts vendus par article.
 *
 * Topologie complète :
 *
 *   payment-events (source)
 *        │
 *        │  KStream<orderId, PaymentSucceededEvent>
 *        │
 *        ├─[1] filter      → garde uniquement les événements valides
 *        │
 *        ├─[2] peek        → log pédagogique (sans transformation)
 *        │
 *        ├─[3] selectKey   → re-clé par itemId (pour le groupBy)
 *        │         KStream<itemId, PaymentSucceededEvent>
 *        │
 *        ├─[4] mapValues   → extrait la quantité vendue (Long)
 *        │         KStream<itemId, Long>
 *        │
 *        ├─[5] groupByKey  → regroupe par itemId
 *        │         KGroupedStream<itemId, Long>
 *        │
 *        ├─[6] reduce      → somme des quantités par itemId
 *        │         KTable<itemId, Long>  ← STATE STORE (in-memory)
 *        │
 *        └─[7] toStream → to("analytics-stats")
 *                  KStream<itemId, Long> → topic analytics-stats
 *
 * Concepts Kafka Streams démontrés :
 *  - KStream      : flux non-borné de records
 *  - KTable       : vue matérialisée (changelog compacté) — chaque clé
 *                   a une seule valeur courante (le total cumulé)
 *  - State Store  : stockage local RocksDB/in-memory pour le KTable
 *  - Changelog    : le KTable est automatiquement sauvegardé dans
 *                   un topic interne pour la tolérance aux pannes
 *  - selectKey    : re-clé les messages (déclenche un repartitionnement)
 *  - reduce       : agrégation (ici : somme des quantités)
 *  - Interactive Queries : le store "sales-by-item" peut être interrogé
 *                          depuis l'AnalyticsResource sans passer par Kafka
 * ============================================================
 */
@ApplicationScoped
public class SalesAnalyticsTopology {

    private static final Logger LOG = Logger.getLogger(SalesAnalyticsTopology.class);

    /** Nom du State Store interrogeable via Interactive Queries */
    public static final String SALES_STORE_NAME = "sales-by-item";

    /** Topic source : les paiements validés */
    public static final String SOURCE_TOPIC  = "payment-events";

    /** Topic sink : les statistiques agrégées (consommé par Kafka Connect) */
    public static final String SINK_TOPIC    = "analytics-stats";

    // Jackson pour désérialiser manuellement les bytes Kafka
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();  // enregistre JavaTimeModule pour Instant

    // ─────────────────────────────────────────────────────────────────────────
    // TOPOLOGY PRODUCER METHOD
    // Quarkus Kafka Streams détecte automatiquement la méthode @Produces
    // qui retourne une Topology et l'enregistre auprès de KafkaStreams.
    // ─────────────────────────────────────────────────────────────────────────

    @Produces
    public Topology buildTopology() {
        StreamsBuilder builder = new StreamsBuilder();

        // ── [1] Source Stream ─────────────────────────────────────────────────
        // Lit les messages de payment-events.
        // Les clés sont des String (orderId), les valeurs sont des bytes JSON.
        KStream<String, String> rawStream = builder.stream(
                SOURCE_TOPIC,
                Consumed.with(Serdes.String(), Serdes.String())
        );

        // ── [2] Désérialisation JSON → PaymentSucceededEvent ──────────────────
        KStream<String, PaymentSucceededEvent> paymentStream = rawStream
                .mapValues(json -> {
                    try {
                        return MAPPER.readValue(json, PaymentSucceededEvent.class);
                    } catch (Exception e) {
                        LOG.warnf("[Streams] Impossible de désérialiser le message : %s", e.getMessage());
                        return null; // filtré à l'étape suivante
                    }
                })
                // ── [3] filter : on écarte les messages malformés ─────────────
                .filter((key, event) -> event != null);

        // ── [4] peek : log sans transformation (outil de debug) ───────────────
        paymentStream.peek((orderId, event) ->
                LOG.infof("[Streams] PaymentSucceeded traité : orderId=%s | item=%s | qty=%d | rang=%s",
                          orderId, event.itemName, event.quantity, event.playerRank));

        // ── [5] selectKey : re-clé par itemId ─────────────────────────────────
        // Avant : clé = orderId  →  Après : clé = itemId
        // Ceci déclenche un repartitionnement interne automatique pour
        // que tous les records d'un même itemId aillent sur la même partition.
        KStream<String, Long> quantityStream = paymentStream
                .selectKey((orderId, event) -> event.itemId)
                .mapValues(event -> (long) event.quantity);

        // ── [6] groupByKey + reduce → KTable ──────────────────────────────────
        // groupByKey  : regroupe les records par clé (itemId)
        // reduce      : additionne les quantités → total vendu par article
        // Materialized.as(storeName) : nomme le State Store pour les Interactive Queries
        KeyValueBytesStoreSupplier storeSupplier = Stores.inMemoryKeyValueStore(SALES_STORE_NAME);

        KTable<String, Long> salesTable = quantityStream
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
                .reduce(
                        Long::sum,  // (totalPrécédent, nouvelleQuantité) → nouveau total
                        Materialized.<String, Long>as(storeSupplier)
                                    .withKeySerde(Serdes.String())
                                    .withValueSerde(Serdes.Long())
                );

        // ── [7] Sink : publication des agrégats dans analytics-stats ──────────
        // salesTable.toStream() convertit le KTable en KStream de changelog.
        // Chaque mise à jour du total produit un nouveau record dans le topic.
        // Format : clé = itemId, valeur = total vendu (Long converti en String)
        salesTable.toStream()
                  .peek((itemId, total) ->
                          LOG.infof("[Streams] 📊 Stat mise à jour : item='%s' → %d vendus au total",
                                    itemId, total))
                  .mapValues(total -> Long.toString(total))  // String pour Kafka Connect
                  .to(SINK_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        Topology topology = builder.build();

        // Log de la topologie Kafka Streams (utile pour la démo pédagogique)
        LOG.infof("[Streams] Topologie construite :%n%s", topology.describe());

        return topology;
    }
}
