package upjv.insset.kafka.consumers;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import upjv.insset.kafka.events.RankVerifiedEvent;
import upjv.insset.kafka.events.StockReservedEvent;
import upjv.insset.kafka.events.StockFailedEvent;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ============================================================
 * StockService – Mock Consumer + Producer (Saga Step 3)
 * ============================================================
 *
 * Rôle dans la Saga :
 *   Consomme RankVerified (rank-events)
 *   Vérifie et décrémente le stock virtuel (ConcurrentHashMap)
 *   Publie StockReserved (stock-events) si stock dispo,
 *   ou abandonne la saga si rang KO ou stock vide.
 *
 * Stock virtuel :
 *   Chaque article du catalogue commence avec 50 unités.
 *   AtomicInteger garantit des opérations de décrément
 *   atomiques (thread-safety) sans verrou explicite.
 *
 * Concepts Kafka démontrés :
 *  - Exactly-once sémantique simulée :
 *    on décrémente en mémoire PUIS on publie l'événement.
 *    En production, on utiliserait les transactions Kafka.
 *  - Métriques Gauge : niveau de stock en temps réel dans Grafana.
 * ============================================================
 */
@ApplicationScoped
public class StockService {

    private static final Logger LOG = Logger.getLogger(StockService.class);

    // ── Stock virtuel initial (augmenté pour tests de performance) ───────────────────────────
    private final Map<String, AtomicInteger> stockLevels = new ConcurrentHashMap<>(Map.of(
            "tshirt-bronze",    new AtomicInteger(1_000_000_000),
            "tshirt-argent",    new AtomicInteger(1_000_000_000),
            "hoodie-or",        new AtomicInteger(1_000_000_000),
            "casquette-platine",new AtomicInteger(1_000_000_000),
            "tshirt-diamant",   new AtomicInteger(1_000_000_000),
            "veste-challenger", new AtomicInteger(1_000_000_000)
    ));

    @Inject
    @Channel("stock-out")
    Emitter<StockReservedEvent> stockEmitter;

    @Inject
    @Channel("stock-failed-out")
    Emitter<StockFailedEvent> stockFailedEmitter;

    private final Counter stockReservedCounter;
    private final Counter stockFailedCounter;

    @Inject
    public StockService(MeterRegistry registry) {
        this.stockReservedCounter = Counter.builder("tuuuur.stock.reserved")
                .description("Réservations de stock réussies")
                .register(registry);
        this.stockFailedCounter = Counter.builder("tuuuur.stock.failed")
                .description("Réservations de stock échouées (rupture ou rang KO)")
                .register(registry);

        // ── Gauges Micrometer : niveau de stock visible dans Grafana ──────────
        stockLevels.forEach((itemId, stock) ->
                Gauge.builder("tuuuur.stock.level", stock, AtomicInteger::get)
                     .tag("item", itemId)
                     .description("Niveau de stock en temps réel par article")
                     .register(registry)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSUMER : rank-events → vérification et réservation du stock
    // ─────────────────────────────────────────────────────────────────────────

    @Incoming("rank-in")
    @Blocking
    public CompletionStage<Void> onRankVerified(Message<RankVerifiedEvent> message) {
        RankVerifiedEvent event = message.getPayload();

        LOG.infof("📦 [Stock] RankVerified reçu : orderId=%s | item=%s | rankOk=%b",
                  event.orderId, event.itemName, event.rankOk);

        // ── Abandon de la saga si le rang est insuffisant ─────────────────────
        if (!event.rankOk) {
            LOG.warnf("  ⛔ [Stock] Rang insuffisant pour orderId=%s, saga abandonnée.", event.orderId);
            stockFailedCounter.increment();
            publishStockFailed(event, "RANK_INSUFFICIENT");
            // On ack quand même pour ne pas retraiter ce message
            return message.ack();
        }

        // ── Vérification et décrément du stock ────────────────────────────────
        AtomicInteger stock = stockLevels.get(event.itemId);

        if (stock == null) {
            LOG.errorf("  ❌ [Stock] Article inconnu dans le stock : %s", event.itemId);
            stockFailedCounter.increment();
            publishStockFailed(event, "UNKNOWN_ITEM");
            return message.ack(); // article inexistant → ack sans publier
        }

        // compareAndSet en boucle = décrément atomique garantissant : stock >= qty
        int currentStock;
        int newStock;
        boolean reserved = false;
        do {
            currentStock = stock.get();
            if (currentStock < event.quantity) {
                break; // Rupture de stock
            }
            newStock = currentStock - event.quantity;
            reserved = stock.compareAndSet(currentStock, newStock);
        } while (!reserved);

        if (!reserved) {
            LOG.warnf("  📭 [Stock] Rupture de stock pour '%s' (stock=%d, demandé=%d)",
                      event.itemId, stock.get(), event.quantity);
            stockFailedCounter.increment();
            publishStockFailed(event, "INSUFFICIENT_STOCK");
            // Saga abandonnée – pas d'événement publié vers payment-events
            return message.ack();
        }

        int remaining = stock.get();
        LOG.infof("  ✅ [Stock] Réservé %d x '%s' | Stock restant : %d",
                  event.quantity, event.itemName, remaining);
        stockReservedCounter.increment();

        // ── Publication de StockReservedEvent ─────────────────────────────────
        StockReservedEvent stockEvent = StockReservedEvent.from(event, remaining);

        // En SmallRye 4.x (Quarkus 3.9+), send(Message) est void (fire-and-forget).
        try {
            stockEmitter.send(KafkaRecord.of(event.orderId, stockEvent));
            LOG.infof("  📤 StockReserved publié [orderId=%s]", event.orderId);
            return message.ack();
        } catch (Exception ex) {
            LOG.errorf("  ❌ Erreur publication StockReserved : %s", ex.getMessage());
            // Rollback du stock en cas d'échec Kafka
            stock.addAndGet(event.quantity);
            LOG.warnf("  ↩️  Stock remis à %d pour '%s' (rollback)", stock.get(), event.itemId);
            return message.nack(ex);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Consultation du stock (endpoint REST optionnel)
    // ─────────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────
    // Helper : publier un événement d'échec de stock
    // ─────────────────────────────────────────────────────────────────────────

    private void publishStockFailed(RankVerifiedEvent rankEvent, String reason) {
        try {
            StockFailedEvent failedEvent = StockFailedEvent.from(rankEvent, reason);
            stockFailedEmitter.send(KafkaRecord.of(rankEvent.orderId, failedEvent));
            LOG.warnf("  📤 StockFailed publié [orderId=%s, reason=%s]", rankEvent.orderId, reason);
        } catch (Exception ex) {
            LOG.errorf("  ❌ Erreur publication StockFailed : %s", ex.getMessage());
        }
    }

    public Map<String, Integer> getStockSnapshot() {
        Map<String, Integer> snapshot = new ConcurrentHashMap<>();
        stockLevels.forEach((k, v) -> snapshot.put(k, v.get()));
        return snapshot;
    }
}
