package upjv.insset.model;

import java.time.Instant;

/**
 * Événement produit par le Payment Service (mock) après simulation du paiement.
 * Publishé sur le topic : payment-events
 *
 * Dans cette démo, le paiement est TOUJOURS un succès (Thread.sleep de 1s).
 */
public class PaymentSucceededEvent {

    public String orderId;
    public String playerId;
    public String playerName;
    public PlayerRank playerRank;
    public String itemId;
    public String itemName;
    public int quantity;
    public double totalAmount;        // quantity * unitPrice
    public String transactionId;      // ID de transaction fictif
    public Instant paidAt;

    public PaymentSucceededEvent() {}

    /** Construit depuis un StockReservedEvent. */
    public static PaymentSucceededEvent from(StockReservedEvent stock, String transactionId) {
        var event = new PaymentSucceededEvent();
        event.orderId       = stock.orderId;
        event.playerId      = stock.playerId;
        event.playerName    = stock.playerName;
        event.playerRank    = stock.playerRank;
        event.itemId        = stock.itemId;
        event.itemName      = stock.itemName;
        event.quantity      = stock.quantity;
        event.totalAmount   = stock.quantity * stock.unitPrice;
        event.transactionId = transactionId;
        event.paidAt        = Instant.now();
        return event;
    }

    @Override
    public String toString() {
        return "PaymentSucceededEvent{orderId='" + orderId + "', amount=" + totalAmount +
               ", txId='" + transactionId + "'}";
    }
}
