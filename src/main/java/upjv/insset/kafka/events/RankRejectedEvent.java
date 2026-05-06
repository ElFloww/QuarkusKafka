package upjv.insset.kafka.events;

import java.time.Instant;

/**
 * Événement produit par le GameSync Service quand le rang est insuffisant.
 * Publishé sur le topic : rank-events
 */
public class RankRejectedEvent {

    public String orderId;
    public String playerId;
    public String playerName;
    public upjv.insset.shared.model.PlayerRank playerRank;
    public upjv.insset.shared.model.PlayerRank requiredRank;
    public String itemName;
    public String reason;             // Ex: "INSUFFICIENT_RANK"
    public Instant rejectedAt;

    public RankRejectedEvent() {}

    /** Construit un RankRejectedEvent depuis un OrderCreatedEvent. */
    public static RankRejectedEvent from(OrderCreatedEvent order, upjv.insset.shared.model.PlayerRank requiredRank) {
        var event = new RankRejectedEvent();
        event.orderId        = order.orderId;
        event.playerId       = order.playerId;
        event.playerName     = order.playerName;
        event.playerRank     = order.playerRank;
        event.requiredRank   = requiredRank;
        event.itemName       = order.itemName;
        event.reason         = "INSUFFICIENT_RANK";
        event.rejectedAt     = Instant.now();
        return event;
    }

    @Override
    public String toString() {
        return "RankRejectedEvent{orderId='" + orderId + "', playerRank=" + playerRank + ", requiredRank=" + requiredRank + "}";
    }
}
