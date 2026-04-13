package upjv.insset.api.shop;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import upjv.insset.shared.infrastructure.MerchCatalog;
import upjv.insset.shared.model.PlayerRank;
import upjv.insset.api.order.OrderService;

/**
 * Sert la page web de la boutique Tuuuur Merch.
 *
 * Le template Qute (resources/templates/shop.html) reçoit :
 *  - items   : liste des articles du catalogue
 *  - orders  : liste des commandes en mémoire (pour le tableau de suivi)
 *  - ranks   : liste des rangs pour le formulaire select
 */
@Path("/")
public class ShopResource {

    @Inject
    Template shop;           // Qute cherche resources/templates/shop.html

    @Inject
    OrderService orderService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance shopPage() {
        return shop
                .data("items",  MerchCatalog.ITEMS)
                .data("orders", orderService.listOrders())
                .data("ranks",  PlayerRank.values());
    }
}
