package upjv.insset.shared.model;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Désérialiseur Kafka JSON → PaymentFailedEvent. */
public class PaymentFailedEventDeserializer extends ObjectMapperDeserializer<upjv.insset.kafka.events.PaymentFailedEvent> {
    public PaymentFailedEventDeserializer() {
        super(upjv.insset.kafka.events.PaymentFailedEvent.class);
    }
}
