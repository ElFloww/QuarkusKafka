package upjv.insset.api.order;

import upjv.insset.shared.model.PlayerRank;

import java.time.Instant;

/**
 * Représentation d'une commande dans la boutique Tuuuur Merch.
 * Stockée en mémoire (ConcurrentHashMap) dans l'OrderRepository.
 *
 * Le statut évolue au fur et à mesure que les événements de la Saga
 * sont consommés par l'OrderConfirmationConsumer.
 */
public class Order {

    public String orderId;
    public String playerId;
    public String playerName;
    public PlayerRank playerRank;
    public String itemId;
    public String itemName;
    public int quantity;
    public double totalAmount;
    public OrderStatus status;
    public String transactionId;      // rempli quand CONFIRMED
    public Instant createdAt;
    public Instant updatedAt;

    public Order() {}

    public Order(String orderId, String playerId, String playerName,
                 PlayerRank playerRank, String itemId, String itemName,
                 int quantity, double unitPrice) {
        this.orderId      = orderId;
        this.playerId     = playerId;
        this.playerName   = playerName;
        this.playerRank   = playerRank;
        this.itemId       = itemId;
        this.itemName     = itemName;
        this.quantity     = quantity;
        this.totalAmount  = quantity * unitPrice;
        this.status       = OrderStatus.PENDING;
        this.createdAt    = Instant.now();
        this.updatedAt    = Instant.now();
    }

    /** Met à jour le statut et la date de modification. */
    public synchronized void updateStatus(OrderStatus newStatus) {
        this.status    = newStatus;
        this.updatedAt = Instant.now();
    }

    @Override
    public String toString() {
        return "Order{id='" + orderId + "', item='" + itemName +
               "', status=" + status + ", player='" + playerName + "'}";
    }
}
