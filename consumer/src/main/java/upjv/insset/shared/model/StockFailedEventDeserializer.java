package upjv.insset.shared.model;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Désérialiseur Kafka JSON → StockFailedEvent. */
public class StockFailedEventDeserializer extends ObjectMapperDeserializer<upjv.insset.kafka.events.StockFailedEvent> {
    public StockFailedEventDeserializer() {
        super(upjv.insset.kafka.events.StockFailedEvent.class);
    }
}
