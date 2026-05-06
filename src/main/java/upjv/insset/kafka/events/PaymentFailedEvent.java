package upjv.insset.kafka.events;

import java.time.Instant;

/**
 * Événement produit par le Payment Service quand le paiement échoue.
 * Publishé sur le topic : payment-events
 */
public class PaymentFailedEvent {

    public String orderId;
    public String playerId;
    public String playerName;
    public upjv.insset.shared.model.PlayerRank playerRank;
    public String itemId;
    public String itemName;
    public double totalAmount;
    public String reason;             // "INSUFFICIENT_FUNDS", "CARD_DECLINED", etc
    public Instant failedAt;

    public PaymentFailedEvent() {}

    /** Construit un PaymentFailedEvent depuis un StockReservedEvent. */
    public static PaymentFailedEvent from(StockReservedEvent stock, String reason) {
        var event = new PaymentFailedEvent();
        event.orderId        = stock.orderId;
        event.playerId       = stock.playerId;
        event.playerName     = stock.playerName;
        event.playerRank     = stock.playerRank;
        event.itemId         = stock.itemId;
        event.itemName       = stock.itemName;
        event.totalAmount    = stock.unitPrice * stock.quantity;
        event.reason         = reason;
        event.failedAt       = Instant.now();
        return event;
    }

    @Override
    public String toString() {
        return "PaymentFailedEvent{orderId='" + orderId + "', reason='" + reason + "'}";
    }
}
