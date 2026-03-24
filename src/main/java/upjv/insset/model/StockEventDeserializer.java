package upjv.insset.model;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Désérialiseur Kafka JSON → StockReservedEvent (utilisé par le Payment Service). */
public class StockEventDeserializer extends ObjectMapperDeserializer<StockReservedEvent> {
    public StockEventDeserializer() {
        super(StockReservedEvent.class);
    }
}
