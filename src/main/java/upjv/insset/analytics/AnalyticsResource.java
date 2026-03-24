package upjv.insset.analytics;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Endpoint REST pour consulter les statistiques de ventes en temps réel.
 *
 * GET /api/analytics/sales              → toutes les stats
 * GET /api/analytics/sales/{itemId}     → ventes d'un article
 * GET /api/analytics/streams/state      → état du KafkaStreams pipeline
 */
@Path("/api/analytics")
@Produces(MediaType.APPLICATION_JSON)
public class AnalyticsResource {

    @Inject
    SalesQueryService salesQueryService;

    /** Toutes les statistiques de ventes (via Interactive Queries) */
    @GET
    @Path("/sales")
    public Map<String, Long> getAllSales() {
        return salesQueryService.getAllSalesStats();
    }

    /** Ventes pour un article précis */
    @GET
    @Path("/sales/{itemId}")
    public Response getSalesForItem(@PathParam("itemId") String itemId) {
        long total = salesQueryService.getSalesForItem(itemId);
        return Response.ok(Map.of("itemId", itemId, "totalSold", total)).build();
    }

    /** État du pipeline Kafka Streams (RUNNING, REBALANCING, ERROR…) */
    @GET
    @Path("/streams/state")
    public Map<String, String> getStreamsState() {
        return Map.of("state", salesQueryService.getStreamsState());
    }
}
