package upjv.insset.shared.model;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Désérialiseur Kafka JSON → OrderCreatedEvent (utilisé par les consumers). */
public class OrderEventDeserializer extends ObjectMapperDeserializer<upjv.insset.kafka.events.OrderCreatedEvent> {
    public OrderEventDeserializer() {
        super(upjv.insset.kafka.events.OrderCreatedEvent.class);
    }
}
