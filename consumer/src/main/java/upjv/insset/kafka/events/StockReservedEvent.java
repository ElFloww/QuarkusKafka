package upjv.insset.kafka.events;

import java.time.Instant;

/**
 * Événement produit par le Stock Service après réservation du stock.
 * Publishé sur le topic : stock-events
 */
public class StockReservedEvent {

    public String orderId;
    public String playerId;
    public String playerName;
    public upjv.insset.shared.model.PlayerRank  playerRank;
    public String itemId;
    public String itemName;
    public int quantity;
    public double unitPrice;
    public int remainingStock;        // Stock restant après réservation
    public Instant reservedAt;

    public StockReservedEvent() {}

    /** Construit un StockReservedEvent depuis un RankVerifiedEvent. */
    public static StockReservedEvent from(RankVerifiedEvent rank, int remainingStock) {
        var event = new StockReservedEvent();
        event.orderId        = rank.orderId;
        event.playerId       = rank.playerId;
        event.playerName     = rank.playerName;
        event.playerRank     = rank.playerRank;
        event.itemId         = rank.itemId;
        event.itemName       = rank.itemName;
        event.quantity       = rank.quantity;
        event.unitPrice      = rank.unitPrice;
        event.remainingStock = remainingStock;
        event.reservedAt     = Instant.now();
        return event;
    }

    @Override
    public String toString() {
        return "StockReservedEvent{orderId='" + orderId + "', item='" + itemName +
                "', remaining=" + remainingStock + "}";
    }
}
