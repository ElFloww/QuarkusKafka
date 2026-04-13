package upjv.insset.shared.model;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Désérialiseur Kafka JSON → StockReservedEvent (utilisé par le Payment Service). */
public class StockEventDeserializer extends ObjectMapperDeserializer<upjv.insset.kafka.events.StockReservedEvent> {
    public StockEventDeserializer() {
        super(upjv.insset.kafka.events.StockReservedEvent.class);
    }
}
