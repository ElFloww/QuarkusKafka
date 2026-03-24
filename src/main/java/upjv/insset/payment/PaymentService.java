package upjv.insset.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import upjv.insset.model.PaymentSucceededEvent;
import upjv.insset.model.StockReservedEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * ============================================================
 * PaymentService – Mock Consumer + Producer (Saga Step 4)
 * ============================================================
 *
 * Rôle dans la Saga :
 *   Consomme StockReserved (stock-events)
 *   Simule un traitement de paiement (Thread.sleep 1 seconde)
 *   Retourne TOUJOURS succès (pas de vrai système de paiement)
 *   Publie PaymentSucceeded (payment-events)
 *
 * Le Thread.sleep simule la latence d'un appel à une passerelle
 * de paiement externe (Stripe, PayPal, etc.).
 * @Blocking est OBLIGATOIRE ici : Thread.sleep sur un thread
 * event-loop bloquerait tout le système Vert.x.
 *
 * Concepts Kafka démontrés :
 *  - @Blocking avec opération longue (sleep 1s)
 *  - Timer Micrometer : mesure la durée réelle du "paiement"
 *  - CompletableFuture.runAsync() : on décale le sleep sur un
 *    thread worker pour ne pas bloquer l'event-loop Vert.x,
 *    tout en restant réactif sur le retour.
 *
 * Note : en production, ce serait remplacé par un appel HTTP
 * non-bloquant (Quarkus REST Client Reactive) vers l'API Stripe.
 * ============================================================
 */
@ApplicationScoped
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    /** Durée simulée du traitement de paiement (en millisecondes) */
    private static final long PAYMENT_PROCESSING_DELAY_MS = 1_000L;

    @Inject
    @Channel("payment-out")
    Emitter<PaymentSucceededEvent> paymentEmitter;

    private final Counter paymentsProcessed;
    private final Timer   paymentTimer;

    @Inject
    public PaymentService(MeterRegistry registry) {
        this.paymentsProcessed = Counter.builder("tuuuur.payment.processed")
                .description("Nombre de paiements simulés traités")
                .register(registry);
        // Timer pour mesurer la durée de traitement dans Grafana
        this.paymentTimer = Timer.builder("tuuuur.payment.duration")
                .description("Durée de traitement du mock paiement")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSUMER : stock-events → simulation paiement → payment-events
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @Blocking est INDISPENSABLE car ce consumer contient un Thread.sleep.
     * Sans @Blocking, SmallRye exécuterait ce code sur le thread event-loop
     * Vert.x, bloquant ainsi TOUS les I/O de l'application.
     * Avec @Blocking, SmallRye délègue l'exécution à un pool de threads worker.
     */
    @Incoming("stock-in")
    @Blocking
    public CompletionStage<Void> onStockReserved(Message<StockReservedEvent> message) {
        StockReservedEvent event = message.getPayload();

        LOG.infof("💳 [Payment] StockReserved reçu : orderId=%s | article=%s | montant=%.2f€",
                  event.orderId, event.itemName, event.quantity * event.unitPrice);

        // Enregistrement de la durée grâce au Timer Micrometer
        return CompletableFuture.runAsync(() -> simulatePaymentProcessing(event))
                .thenCompose(v -> publishPaymentSucceeded(event, message))
                .exceptionally(ex -> {
                    LOG.errorf("  ❌ [Payment] Erreur inattendue pour orderId=%s : %s",
                               event.orderId, ex.getMessage());
                    message.nack(ex);
                    return null;
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Simulation du traitement de paiement (Thread.sleep intentionnel)
    // ─────────────────────────────────────────────────────────────────────────

    private void simulatePaymentProcessing(StockReservedEvent event) {
        LOG.infof("  ⏳ [Payment] Traitement du paiement en cours pour orderId=%s …", event.orderId);

        paymentTimer.record(() -> {
            try {
                // ── SIMULATION DE LATENCE BANCAIRE ───────────────────────────
                // Thread.sleep représente l'appel à la passerelle de paiement.
                // En production : remplacer par un appel REST réactif non-bloquant.
                Thread.sleep(PAYMENT_PROCESSING_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Payment processing interrupted", e);
            }
        });

        // Dans cette démo, le paiement RETOURNE TOUJOURS SUCCÈS
        LOG.infof("  💰 [Payment] Paiement validé (mock) pour orderId=%s", event.orderId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Publication de PaymentSucceededEvent
    // ─────────────────────────────────────────────────────────────────────────

    private CompletionStage<Void> publishPaymentSucceeded(StockReservedEvent event,
                                                           Message<StockReservedEvent> originalMessage) {
        // Génération d'un ID de transaction fictif (format TX-XXXXXXXX)
        String transactionId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PaymentSucceededEvent paymentEvent = PaymentSucceededEvent.from(event, transactionId);

        paymentsProcessed.increment();

        // En SmallRye 4.x (Quarkus 3.9+), send(Message) est void (fire-and-forget).
        try {
            paymentEmitter.send(KafkaRecord.of(event.orderId, paymentEvent));
            LOG.infof("  ✅ [Payment] PaymentSucceeded publié [orderId=%s | txId=%s | total=%.2f€]",
                      event.orderId, transactionId, paymentEvent.totalAmount);
            return originalMessage.ack();
        } catch (Exception ex) {
            LOG.errorf("  ❌ [Payment] Échec publication PaymentSucceeded : %s", ex.getMessage());
            return originalMessage.nack(ex);
        }
    }
}
