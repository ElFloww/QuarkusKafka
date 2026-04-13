package upjv.insset.shared.infrastructure;

import upjv.insset.shared.model.PlayerRank;

import java.util.List;

/**
 * Catalogue des articles de merch disponibles dans la boutique Tuuuur.
 *
 * Chaque article est associé à un rang minimum requis, illustrant
 * la règle métier vérifiée par le GameSync Service dans la Saga.
 */
public class MerchCatalog {

    public record MerchItem(
            String id,
            String name,
            String description,
            double price,
            PlayerRank requiredRank,
            String imageUrl        // URL relative vers /static/images/
    ) {}

    public static final List<MerchItem> ITEMS = List.of(

        new MerchItem(
            "tshirt-bronze",
            "T-Shirt Rang Bronze",
            "Pour les débutants courageux. Prouve que tu as commencé ton aventure sur Tuuuur.",
            19.99,
            PlayerRank.BRONZE,
            "/static/img/tshirt-bronze.png"
        ),
        new MerchItem(
            "tshirt-argent",
            "T-Shirt Rang Argent",
            "Tu commences à maîtriser la culture générale. Porte-le avec fierté.",
            24.99,
            PlayerRank.ARGENT,
            "/static/img/tshirt-argent.png"
        ),
        new MerchItem(
            "hoodie-or",
            "Hoodie Rang Or",
            "Exclusif Or. Confortable et stylé, pour les joueurs sérieux de Tuuuur.",
            49.99,
            PlayerRank.OR,
            "/static/img/hoodie-or.png"
        ),
        new MerchItem(
            "casquette-platine",
            "Casquette Platine Edition",
            "Accès réservé aux élites Platine. Bord brodé avec le logo Tuuuur.",
            34.99,
            PlayerRank.PLATINE,
            "/static/img/casquette-platine.png"
        ),
        new MerchItem(
            "tshirt-diamant",
            "T-Shirt Rang Diamant",
            "Le graal du merch. Réservé aux joueurs Diamant. Tissu premium, édition limitée.",
            59.99,
            PlayerRank.DIAMANT,
            "/static/img/tshirt-diamant.png"
        ),
        new MerchItem(
            "veste-challenger",
            "Veste Challenger Exclusive",
            "ULTRA RARE. Seulement pour les Challengers. Numérotée et signée par l'équipe Tuuuur.",
            149.99,
            PlayerRank.CHALLENGER,
            "/static/img/veste-challenger.png"
        )
    );

    public static MerchItem findById(String id) {
        return ITEMS.stream()
                    .filter(item -> item.id().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Article inconnu : " + id));
    }

    private MerchCatalog() {}
}
