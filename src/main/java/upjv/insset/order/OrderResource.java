package upjv.insset.order;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import upjv.insset.model.PlayerRank;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

/**
 * ============================================================
 * OrderResource – API REST de l'Order Service
 * ============================================================
 *
 * Endpoints :
 *   POST /api/orders          → Passer une commande (démarre la Saga)
 *   GET  /api/orders          → Lister toutes les commandes (suivi en direct)
 *   GET  /api/orders/{id}     → Détail d'une commande
 *
 * Le POST est le point d'entrée unique de toute la Saga :
 *   1. Valide les paramètres
 *   2. Délègue à OrderService qui crée la commande et publie sur Kafka
 *   3. Retourne HTTP 202 Accepted (la saga continue de façon asynchrone)
 *
 * HTTP 202 (vs 201 Created) est intentionnel : la commande est PENDING,
 * pas encore CONFIRMED — c'est l'essence de l'architecture Event-Driven.
 */
@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    private static final Logger LOG = Logger.getLogger(OrderResource.class);

    @Inject
    OrderService orderService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/orders – Créer une commande
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Body JSON attendu :
     * {
     *   "playerId":   "player-42",
     *   "playerName": "Kévin_Diamant",
     *   "playerRank": "DIAMANT",
     *   "itemId":     "tshirt-diamant",
     *   "quantity":   1
     * }
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> placeOrder(PlaceOrderRequest request) {
        LOG.infof("→ POST /api/orders | player=%s [%s] | item=%s | qty=%d",
                  request.playerName, request.playerRank, request.itemId, request.quantity);

        // Validation minimale
        if (request.playerId == null || request.itemId == null || request.playerRank == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("{\"error\":\"playerId, itemId et playerRank sont obligatoires\"}")
                            .build()
            );
        }
        if (request.quantity <= 0) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("{\"error\":\"La quantité doit être >= 1\"}")
                            .build()
            );
        }

        return orderService.placeOrder(
                request.playerId,
                request.playerName,
                request.playerRank,
                request.itemId,
                request.quantity
        ).thenApply(order ->
                // HTTP 202 Accepted : la saga est lancée, la commande est PENDING
                Response.accepted(order).build()
        ).exceptionally(ex -> {
            LOG.errorf("Erreur placeOrder : %s", ex.getMessage());
            return Response.serverError()
                           .entity("{\"error\":\"" + ex.getMessage() + "\"}")
                           .build();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/orders – Lister toutes les commandes
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    public Collection<Order> listOrders() {
        return orderService.listOrders();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/orders/{id} – Détail d'une commande
    // ─────────────────────────────────────────────────────────────────────────

    @GET
    @Path("/{orderId}")
    public Response getOrder(@PathParam("orderId") String orderId) {
        Order order = orderService.findOrder(orderId);
        if (order == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("{\"error\":\"Commande introuvable : " + orderId + "\"}")
                           .build();
        }
        return Response.ok(order).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTO de requête
    // ─────────────────────────────────────────────────────────────────────────

    public static class PlaceOrderRequest {
        public String     playerId;
        public String     playerName;
        public PlayerRank playerRank;
        public String     itemId;
        public int        quantity = 1;
    }
}
