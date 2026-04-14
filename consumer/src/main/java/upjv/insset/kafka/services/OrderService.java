package upjv.insset.kafka.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;
import upjv.insset.kafka.events.OrderCreatedEvent;
import upjv.insset.shared.infrastructure.MerchCatalog;
import upjv.insset.shared.model.PlayerRank;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * ============================================================
 * OrderService – Producer API démonstration
 * ============================================================
 *
 * Responsabilités :
 *  1. Créer une commande et la persister dans l'OrderRepository
 *  2. Publier l'événement OrderCreated sur le topic orders-events
 *     via l'@Channel SmallRye Reactive Messaging (Producer API)
 *  3. Exposer les métriques métier via Micrometer
 *
 * Concepts Kafka démontrés :
 *  - Emitter<T>  : API SmallRye pour produire des messages de façon
 *                  impérative (non-réactive) depuis un Bean CDI.
 *  - KafkaRecord : permet de spécifier explicitement la clé du message
 *                  (ici : orderId) pour garantir l'ordre par commande
 *                  sur une même partition.
 *  - send()      : retourne un CompletionStage – on peut attendre l'ack
 *                  du broker avant de confirmer la réponse HTTP.
 * ============================================================
 */
@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    // ── Injection du channel outgoing défini dans application.properties ──────
    // mp.messaging.outgoing.orders-out.topic=orders-events
    @Inject
    @Channel("orders-out")
    Emitter<OrderCreatedEvent> orderEmitter;

    @Inject
    OrderRepository orderRepository;

    // ── Métriques Micrometer ───────────────────────────────────────────────────
    private final Counter ordersCreatedCounter;
    private final Counter ordersConfirmedCounter;

    @Inject
    public OrderService(MeterRegistry registry) {
        // Ces compteurs seront visibles sur /q/metrics et dans Grafana
        this.ordersCreatedCounter  = Counter.builder("tuuuur.orders.created")
                .description("Nombre total de commandes créées")
                .register(registry);
        this.ordersConfirmedCounter = Counter.builder("tuuuur.orders.confirmed")
                .description("Nombre total de commandes confirmées (PaymentSucceeded)")
                .register(registry);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CRÉER UNE COMMANDE – Point d'entrée principal de la Saga
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Crée une commande et publie l'événement OrderCreated sur Kafka.
     *
     * @param playerId    Identifiant du joueur (ex: "player-42")
     * @param playerName  Pseudo du joueur (ex: "Kévin_Diamant")
     * @param playerRank  Rang actuel du joueur dans Tuuuur
     * @param itemId      Identifiant de l'article du catalogue
     * @param quantity    Quantité commandée
     * @return CompletionStage<Order> résolue quand le broker a accusé réception
     */
    public CompletionStage<Order> placeOrder(String playerId, String playerName,
                                             PlayerRank playerRank, String itemId,
                                             int quantity) {
        // 1. Récupérer l'article dans le catalogue
        MerchCatalog.MerchItem item = MerchCatalog.findById(itemId);

        // 2. Construire l'événement OrderCreated
        OrderCreatedEvent event = OrderCreatedEvent.of(
                playerId, playerName, playerRank,
                item.id(), item.name(), item.requiredRank(),
                quantity, item.price()
        );

        // 3. Créer et persister l'entité Order en mémoire (statut PENDING)
        Order order = new Order(
                event.orderId, playerId, playerName, playerRank,
                item.id(), item.name(), quantity, item.price()
        );
        orderRepository.save(order);
        ordersCreatedCounter.increment();

        LOG.infof("📦 Commande créée : %s | Article : %s | Joueur : %s [%s]",
                event.orderId, item.name(), playerName, playerRank);

        // 4. Publier sur Kafka via l'Emitter
        //    KafkaRecord.of(key, value) : la clé = orderId assure que tous
        //    les événements d'une même commande vont sur la même partition.
        //    En SmallRye 4.x (Quarkus 3.9+), send(Message) est fire-and-forget (void).
        try {
            orderEmitter.send(KafkaRecord.of(event.orderId, event));
            LOG.infof("✅ OrderCreated publié sur 'orders-events' [key=%s]", event.orderId);
        } catch (Exception ex) {
            LOG.errorf("❌ Échec publication OrderCreated [orderId=%s] : %s",
                    event.orderId, ex.getMessage());
            order.updateStatus(OrderStatus.PAYMENT_FAILED);
            return CompletableFuture.failedFuture(ex);
        }

        // 5. Retourner l'Order immédiatement (la saga continue de façon async)
        return CompletableFuture.completedFuture(order);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONFIRMER UNE COMMANDE – Appelé par l'OrderConfirmationConsumer
    // ─────────────────────────────────────────────────────────────────────────

    public void confirmOrder(String orderId, String transactionId) {
        orderRepository.setTransactionId(orderId, transactionId);
        orderRepository.updateStatus(orderId, OrderStatus.CONFIRMED);
        ordersConfirmedCounter.increment();
        LOG.infof("🎉 Commande CONFIRMÉE : orderId=%s | txId=%s", orderId, transactionId);
    }

    public void rejectOrder(String orderId, OrderStatus rejectedStatus) {
        orderRepository.updateStatus(orderId, rejectedStatus);
        LOG.warnf("⚠️  Commande rejetée [%s] : orderId=%s", rejectedStatus, orderId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LECTURE
    // ─────────────────────────────────────────────────────────────────────────

    public Order findOrder(String orderId) {
        return orderRepository.findById(orderId);
    }

    public Collection<Order> listOrders() {
        return orderRepository.findAll();
    }
}
