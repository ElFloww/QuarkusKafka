package upjv.insset.shared.model;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Désérialiseur Kafka JSON → RankVerifiedEvent (utilisé par le Stock Service). */
public class RankEventDeserializer extends ObjectMapperDeserializer<upjv.insset.kafka.events.RankVerifiedEvent> {
    public RankEventDeserializer() {
        super(upjv.insset.kafka.events.RankVerifiedEvent.class);
    }
}
