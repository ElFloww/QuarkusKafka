package upjv.insset.kafka.events;

import java.time.Instant;

/**
 * Événement produit par le Stock Service quand la réservation échoue.
 * Raisons possibles : stock insuffisant, article inexistant, rang insuffisant
 * Publishé sur le topic : stock-events
 */
public class StockFailedEvent {

    public String orderId;
    public String playerId;
    public String playerName;
    public upjv.insset.shared.model.PlayerRank playerRank;
    public String itemId;
    public String itemName;
    public int quantity;
    public String reason;             // "INSUFFICIENT_STOCK", "UNKNOWN_ITEM", "RANK_INSUFFICIENT"
    public Instant failedAt;

    public StockFailedEvent() {}

    /** Construit un StockFailedEvent depuis un RankVerifiedEvent. */
    public static StockFailedEvent from(RankVerifiedEvent rank, String reason) {
        var event = new StockFailedEvent();
        event.orderId        = rank.orderId;
        event.playerId       = rank.playerId;
        event.playerName     = rank.playerName;
        event.playerRank     = rank.playerRank;
        event.itemId         = rank.itemId;
        event.itemName       = rank.itemName;
        event.quantity       = rank.quantity;
        event.reason         = reason;
        event.failedAt       = Instant.now();
        return event;
    }

    @Override
    public String toString() {
        return "StockFailedEvent{orderId='" + orderId + "', reason='" + reason + "'}";
    }
}
