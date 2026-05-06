package upjv.insset.api.order;

import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import upjv.insset.kafka.events.RankVerifiedEvent;
import upjv.insset.kafka.events.StockReservedEvent;
import upjv.insset.kafka.events.PaymentSucceededEvent;

import java.util.concurrent.CompletionStage;

/**
 * ============================================================
 * OrderSagaStatusUpdater – Observer des événements intermédiaires
 * ============================================================
 *
 * Ce consumer appartient à l'Order Service mais utilise des
 * groupes consommateurs DIFFÉRENTS de ceux de GameSync et Stock.
 * Il "espionne" les topics intermédiaires pour mettre à jour
 * le statut de la commande en temps réel.
 *
 * L'UI peut ainsi afficher RANK_VERIFIED, STOCK_RESERVED,
 * et pas seulement PENDING → CONFIRMED.
 *
 * Groupes Kafka :
 *   order-service-rank-observer  (≠ gamesync-service-grp)
 *   order-service-stock-observer (≠ stock-service-grp)
 *
 * Cela illustre le concept de Consumer Group :
 * plusieurs groupes peuvent consommer le MÊME topic
 * indépendamment, chacun avec son propre offset.
 * ============================================================
 */
@ApplicationScoped
public class OrderSagaStatusUpdater {

    private static final Logger LOG = Logger.getLogger(OrderSagaStatusUpdater.class);

    @Inject
    OrderRepository orderRepository;

    @Inject
    MeterRegistry meterRegistry;

    // ─────────────────────────────────────────────────────────────────────────
    // Observer rank-events → mise à jour statut RANK_VERIFIED / RANK_REJECTED
    // ─────────────────────────────────────────────────────────────────────────

    @Incoming("rank-observer-in")
    @Blocking
    public CompletionStage<Void> onRankVerified(Message<RankVerifiedEvent> message) {
        RankVerifiedEvent event = message.getPayload();

        OrderStatus newStatus = event.rankOk
                ? OrderStatus.RANK_VERIFIED
                : OrderStatus.RANK_REJECTED;

        orderRepository.updateStatus(event.orderId, newStatus);
        LOG.infof("📊 [StatusUpdater] Order %s → %s", event.orderId, newStatus);

        // Track rank event processing
        meterRegistry.counter("tuuuur.saga.rank.events",
            "status", newStatus.toString(),
            "consumer_group", "order-service-rank-observer").increment();

        return message.ack();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Observer stock-events → mise à jour statut STOCK_RESERVED / STOCK_FAILED
    // ─────────────────────────────────────────────────────────────────────────

    @Incoming("stock-observer-in")
    @Blocking
    public CompletionStage<Void> onStockReserved(Message<StockReservedEvent> message) {
        StockReservedEvent event = message.getPayload();

        // Si stock-events est reçu c'est que le stock a bien été réservé
        orderRepository.updateStatus(event.orderId, OrderStatus.STOCK_RESERVED);
        LOG.infof("📊 [StatusUpdater] Order %s → STOCK_RESERVED (restant: %d)",
                  event.orderId, event.remainingStock);

        // Track stock event processing
        meterRegistry.counter("tuuuur.saga.stock.events",
            "status", "reserved",
            "consumer_group", "order-service-stock-observer").increment();

        return message.ack();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Observer payment-events → mise à jour statut PAYMENT_SUCCEEDED / CONFIRMED
    // ─────────────────────────────────────────────────────────────────────────

    @Incoming("payment-observer-in")
    @Blocking
    public CompletionStage<Void> onPaymentSucceeded(Message<PaymentSucceededEvent> message) {
        PaymentSucceededEvent event = message.getPayload();

        // Mettre à jour le statut à CONFIRMED quand le paiement est réussi
        orderRepository.updateStatus(event.orderId, OrderStatus.CONFIRMED);
        orderRepository.setTransactionId(event.orderId, event.transactionId);
        LOG.infof("📊 [StatusUpdater] Order %s → CONFIRMED (txId: %s)", 
                  event.orderId, event.transactionId);

        // Track payment event processing
        meterRegistry.counter("tuuuur.saga.payment.events",
            "status", "succeeded",
            "consumer_group", "order-service-payment-observer").increment();

        return message.ack();
    }
}
