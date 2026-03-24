package upjv.insset;

/**
 * Point d'entrée Quarkus – Tuuuur Merch Shop
 *
 * En Quarkus, il n'est pas nécessaire d'écrire une méthode main() :
 * le framework génère automatiquement le point d'entrée via le plugin Maven.
 *
 * Pour démarrer l'application en mode développement :
 *
 *   mvn quarkus:dev
 *
 * La Dev UI est ensuite accessible sur : http://localhost:8080/q/dev
 *
 * Architecture de la Saga (Choreography Pattern) :
 *
 *  [UI] → OrderService  ──orders-events──▶ GameSyncService
 *                                                │
 *                                           rank-events
 *                                                │
 *                                          StockService
 *                                                │
 *                                          stock-events
 *                                                │
 *                                         PaymentService (mock)
 *                                                │
 *                                          payment-events
 *                                                │
 *                        OrderService ◀──────────┘  (CONFIRMED)
 *                        AnalyticsService (Kafka Streams)
 */
public class Main {
    // Classe conservée à titre documentaire.
    // Le démarrage est géré par io.quarkus.runner.GeneratedMain (généré au build).
}
