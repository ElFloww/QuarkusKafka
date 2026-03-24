package upjv.insset.model;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Désérialiseur Kafka JSON → OrderCreatedEvent (utilisé par les consumers). */
public class OrderEventDeserializer extends ObjectMapperDeserializer<OrderCreatedEvent> {
    public OrderEventDeserializer() {
        super(OrderCreatedEvent.class);
    }
}
