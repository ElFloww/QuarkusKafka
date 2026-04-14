package upjv.insset.shared.model;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Désérialiseur Kafka JSON → PaymentSucceededEvent (utilisé par l'Order Service). */
public class PaymentEventDeserializer extends ObjectMapperDeserializer<upjv.insset.kafka.events.PaymentSucceededEvent> {
    public PaymentEventDeserializer() {
        super(upjv.insset.kafka.events.PaymentSucceededEvent.class);
    }
}
