package upjv.insset.kafka.consumers;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import upjv.insset.kafka.events.OrderCreatedEvent;
import upjv.insset.kafka.events.RankVerifiedEvent;
import upjv.insset.shared.model.PlayerRank;

import java.util.concurrent.CompletionStage;

/**
 * ============================================================
 * GameSyncService – Mock Consumer + Producer (Saga Step 2)
 * ============================================================
 *
 * Rôle dans la Saga :
 *   Consomme OrderCreated (orders-events)
 *   Valide que le rang du joueur ≥ rang requis pour l'article
 *   Publie RankVerified (rank-events)
 *
 * En production ce service interrogerait l'API de Tuuuur
 * pour vérifier le rang en temps réel. Ici c'est un mock
 * qui effectue la comparaison d'enum localement.
 *
 * Concepts Kafka démontrés :
 *  - @Incoming + @Channel  : Consumer ET Producer dans le même bean
 *  - Traitement conditionnel : on publie RankVerified même si KO
 *    (rankOk=false) pour que la saga puisse se terminer proprement.
 *  - Clé Kafka héritée : on réutilise orderId comme clé de message
 *    pour maintenir l'ordre sur les partitions tout au long de la saga.
 * ============================================================
 */
@ApplicationScoped
public class GameSyncService {

    private static final Logger LOG = Logger.getLogger(GameSyncService.class);

    @Inject
    @Channel("rank-out")
    Emitter<RankVerifiedEvent> rankEmitter;

    private final Counter rankOkCounter;
    private final Counter rankKoCounter;

    @Inject
    public GameSyncService(MeterRegistry registry) {
        this.rankOkCounter = Counter.builder("tuuuur.gamesync.rank.ok")
                .description("Joueurs dont le rang est validé")
                .register(registry);
        this.rankKoCounter = Counter.builder("tuuuur.gamesync.rank.ko")
                .description("Joueurs dont le rang est insuffisant")
                .register(registry);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSUMER : orders-events → validation du rang
    // ─────────────────────────────────────────────────────────────────────────

    @Incoming("orders-in")
    @Blocking          // Opération bloquante : on attend l'ack du producer rank-out
    public CompletionStage<Void> onOrderCreated(Message<OrderCreatedEvent> message) {
        OrderCreatedEvent event = message.getPayload();

        LOG.infof("🎮 [GameSync] OrderCreated reçu : orderId=%s | joueur=%s [%s] | article=%s (requis: %s)",
                  event.orderId, event.playerName, event.playerRank,
                  event.itemName, event.requiredRank);

        // ── Logique de validation du rang ────────────────────────────────────
        // PlayerRank est un enum : ordinal() donne le niveau numérique.
        // BRONZE=0, ARGENT=1, OR=2, PLATINE=3, DIAMANT=4, CHALLENGER=5
        boolean rankOk = isRankSufficient(event.playerRank, event.requiredRank);

        if (rankOk) {
            rankOkCounter.increment();
            LOG.infof("  ✅ Rang valide : %s >= %s", event.playerRank, event.requiredRank);
        } else {
            rankKoCounter.increment();
            LOG.warnf("  ⛔ Rang insuffisant : %s < %s requis pour '%s'",
                      event.playerRank, event.requiredRank, event.itemName);
        }

        // ── Construction et publication de RankVerifiedEvent ─────────────────
        RankVerifiedEvent rankEvent = RankVerifiedEvent.from(event, rankOk);

        // En SmallRye 4.x (Quarkus 3.9+), send(Message) est void (fire-and-forget).
        try {
            rankEmitter.send(KafkaRecord.of(event.orderId, rankEvent));
            LOG.infof("  📤 RankVerified publié [orderId=%s, rankOk=%b]", event.orderId, rankOk);
            return message.ack();
        } catch (Exception ex) {
            LOG.errorf("  ❌ Erreur publication RankVerified : %s", ex.getMessage());
            return message.nack(ex);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Comparaison de rangs via l'ordinal de l'enum
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isRankSufficient(PlayerRank playerRank, PlayerRank requiredRank) {
        return playerRank.ordinal() >= requiredRank.ordinal();
    }
}
