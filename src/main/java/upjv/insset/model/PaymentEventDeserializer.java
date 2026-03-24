package upjv.insset.model;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Désérialiseur Kafka JSON → PaymentSucceededEvent (utilisé par l'Order Service). */
public class PaymentEventDeserializer extends ObjectMapperDeserializer<PaymentSucceededEvent> {
    public PaymentEventDeserializer() {
        super(PaymentSucceededEvent.class);
    }
}
