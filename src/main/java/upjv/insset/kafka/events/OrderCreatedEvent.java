package upjv.insset.kafka.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement produit par l'Order Service quand un client passe commande.
 * Publishé sur le topic : orders-events
 */
public class OrderCreatedEvent {

    public String orderId;
    public String playerId;
    public String playerName;
    public upjv.insset.shared.model.PlayerRank playerRank;      // Rang actuel du joueur dans Tuuuur
    public String itemId;              // ex: "tshirt-diamant"
    public String itemName;            // ex: "T-Shirt Rang Diamant"
    public upjv.insset.shared.model.PlayerRank requiredRank;    // Rang minimum requis pour acheter cet article
    public int quantity;
    public double unitPrice;
    public Instant createdAt;

    public OrderCreatedEvent() {}

    public static OrderCreatedEvent of(String playerId, String playerName,
                                       upjv.insset.shared.model.PlayerRank playerRank, String itemId,
                                       String itemName, upjv.insset.shared.model.PlayerRank requiredRank,
                                       int quantity, double unitPrice) {
        var event = new OrderCreatedEvent();
        event.orderId     = UUID.randomUUID().toString();
        event.playerId    = playerId;
        event.playerName  = playerName;
        event.playerRank  = playerRank;
        event.itemId      = itemId;
        event.itemName    = itemName;
        event.requiredRank = requiredRank;
        event.quantity    = quantity;
        event.unitPrice   = unitPrice;
        event.createdAt   = Instant.now();
        return event;
    }

    @Override
    public String toString() {
        return "OrderCreatedEvent{orderId='" + orderId + "', player='" + playerName +
               "', rank=" + playerRank + ", item='" + itemName + "'}";
    }
}
