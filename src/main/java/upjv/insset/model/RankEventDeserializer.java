package upjv.insset.model;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/** Désérialiseur Kafka JSON → RankVerifiedEvent (utilisé par le Stock Service). */
public class RankEventDeserializer extends ObjectMapperDeserializer<RankVerifiedEvent> {
    public RankEventDeserializer() {
        super(RankVerifiedEvent.class);
    }
}
