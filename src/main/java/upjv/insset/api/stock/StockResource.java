package upjv.insset.api.stock;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * Endpoint REST exposant le niveau de stock en temps réel.
 * Utile pour la démo et le debugging.
 *
 * GET /api/stock → snapshot de tous les stocks
 */
@Path("/api/stock")
@Produces(MediaType.APPLICATION_JSON)
public class StockResource {

    @Inject
    StockService stockService;

    @GET
    public Map<String, Integer> getStock() {
        return stockService.getStockSnapshot();
    }
}
