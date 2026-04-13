package upjv.insset.kafka.events;

import java.time.Instant;

/**
 * Événement produit par le GameSync Service après validation du rang.
 * Publishé sur le topic : rank-events
 */
public class RankVerifiedEvent {

    public String orderId;
    public String playerId;
    public String playerName;
    public upjv.insset.shared.model.PlayerRank playerRank;
    public String itemId;
    public String itemName;
    public upjv.insset.shared.model.PlayerRank requiredRank;
    public int quantity;
    public double unitPrice;
    public boolean rankOk;            // true = joueur autorisé à acheter
    public Instant verifiedAt;

    public RankVerifiedEvent() {}

    /** Construit un RankVerifiedEvent depuis un OrderCreatedEvent. */
    public static RankVerifiedEvent from(OrderCreatedEvent order, boolean rankOk) {
        var event = new RankVerifiedEvent();
        event.orderId      = order.orderId;
        event.playerId     = order.playerId;
        event.playerName   = order.playerName;
        event.playerRank   = order.playerRank;
        event.itemId       = order.itemId;
        event.itemName     = order.itemName;
        event.requiredRank = order.requiredRank;
        event.quantity     = order.quantity;
        event.unitPrice    = order.unitPrice;
        event.rankOk       = rankOk;
        event.verifiedAt   = Instant.now();
        return event;
    }

    @Override
    public String toString() {
        return "RankVerifiedEvent{orderId='" + orderId + "', rankOk=" + rankOk + "}";
    }
}
