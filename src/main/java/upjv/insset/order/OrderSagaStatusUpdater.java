package upjv.insset.order;

import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import upjv.insset.model.RankVerifiedEvent;
import upjv.insset.model.StockReservedEvent;

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

        return message.ack();
    }
}
