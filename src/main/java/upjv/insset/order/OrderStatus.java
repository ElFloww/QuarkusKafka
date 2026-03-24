package upjv.insset.order;

/**
 * Cycle de vie d'une commande dans la Saga Tuuuur Merch.
 *
 *  PENDING
 *    │
 *    ├──[GameSync KO]──▶ RANK_REJECTED
 *    │
 *    ├──[GameSync OK]──▶ RANK_VERIFIED
 *    │                       │
 *    │               [Stock KO]──▶ STOCK_FAILED
 *    │                       │
 *    │               [Stock OK]──▶ STOCK_RESERVED
 *    │                                   │
 *    │                           [Payment KO]──▶ PAYMENT_FAILED
 *    │                                   │
 *    └───────────────────────────[Payment OK]──▶ CONFIRMED
 */
public enum OrderStatus {
    PENDING,
    RANK_VERIFIED,
    RANK_REJECTED,
    STOCK_RESERVED,
    STOCK_FAILED,
    PAYMENT_FAILED,
    CONFIRMED
}
