package upjv.insset.kafka.consumers;

import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import upjv.insset.kafka.events.PaymentSucceededEvent;
import upjv.insset.api.order.OrderService;

import java.util.concurrent.CompletionStage;

/**
 * ============================================================
 * OrderConfirmationConsumer – Consumer API démonstration
 * ============================================================
 *
 * Responsabilité :
 *  Écouter le topic payment-events et mettre à jour le statut
 *  de la commande en CONFIRMED lorsque le PaymentSucceededEvent
 *  est reçu. Boucle la Saga du point de vue de l'Order Service.
 *
 * Concepts Kafka démontrés :
 *  - @Incoming          : annotation SmallRye pour déclarer un consumer.
 *                         Le canal "payment-in" est configuré dans
 *                         application.properties.
 *  - Message<T>         : enveloppe Kafka avec accès au payload, aux
 *                         headers, à la partition, à l'offset.
 *  - ack() / nack()     : gestion explicite d'accusé de réception.
 *                         ack()  → offset commité → message consommé.
 *                         nack() → message remis en erreur (DLQ si configurée).
 *  - @Blocking          : indique que ce consumer peut faire des I/O
 *                         (appel en mémoire ici, mais pattern à respecter).
 * ============================================================
 */
@ApplicationScoped
public class OrderConfirmationConsumer {

    private static final Logger LOG = Logger.getLogger(OrderConfirmationConsumer.class);

    @Inject
    OrderService orderService;

    @Inject
    MeterRegistry meterRegistry;

    /**
     * Consomme les événements PaymentSucceeded du topic payment-events.
     *
     * L'utilisation de Message<T> (au lieu de T directement) donne
     * le contrôle explicite sur l'ack pour éviter la perte de messages.
     */
    @Incoming("payment-in")
    @Blocking
    public CompletionStage<Void> onPaymentSucceeded(Message<PaymentSucceededEvent> message) {
        PaymentSucceededEvent event = message.getPayload();

        LOG.infof("💳 PaymentSucceeded reçu : orderId=%s | txId=%s | montant=%.2f€",
                  event.orderId, event.transactionId, event.totalAmount);

        try {
            // Met à jour la commande en CONFIRMED et enregistre le txId
            orderService.confirmOrder(event.orderId, event.transactionId);

            // ack() : valide l'offset → Kafka sait que le message a été traité
            meterRegistry.counter("tuuuur.confirmation.processed", 
                "status", "success",
                "consumer_group", "order-service-payment-grp").increment();

            return message.ack();

        } catch (Exception e) {
            LOG.errorf("❌ Erreur traitement PaymentSucceeded [orderId=%s] : %s",
                       event.orderId, e.getMessage());

            // nack() : signale l'échec à SmallRye (peut router vers DLQ)
            meterRegistry.counter("tuuuur.confirmation.processed", 
                "status", "error",
                "consumer_group", "order-service-payment-grp").increment();

            return message.nack(e);
        }
    }
}
