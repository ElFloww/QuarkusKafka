package upjv.insset.shared.infrastructure;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * ============================================================
 * KafkaTopicInitializer – Admin API démonstration
 * ============================================================
 *
 * Cette classe utilise l'API AdminClient de Kafka pour créer
 * programmatiquement les topics nécessaires à la Saga au
 * démarrage de l'application (@Observes StartupEvent).
 *
 * Concepts démontrés :
 *  - AdminClient (Kafka Admin API)
 *  - NewTopic : nom, partitions, facteur de réplication
 *  - CreateTopicsResult et gestion idempotente (TopicExistsException)
 *  - Listing des topics existants avant création
 *
 * En mode DevServices, quarkus.kafka.devservices.port=9092
 * injecte automatiquement l'adresse du broker dans
 * kafka.bootstrap.servers – c'est ce que l'on injecte ici.
 * ============================================================
 */
@ApplicationScoped
public class KafkaTopicInitializer {

    private static final Logger LOG = Logger.getLogger(KafkaTopicInitializer.class);

    // ─── Définition des topics de la Saga ─────────────────────────────────────
    //  Partitions = 3 : permet d'avoir 3 consumers en parallèle par groupe.
    //  Réplication = 1 : acceptable en dev (un seul broker).
    private static final List<NewTopic> REQUIRED_TOPICS = List.of(
            new NewTopic("orders-events",    3, (short) 1),
            new NewTopic("rank-events",      3, (short) 1),
            new NewTopic("stock-events",     3, (short) 1),
            new NewTopic("payment-events",   3, (short) 1),
            new NewTopic("analytics-stats",  1, (short) 1)  // 1 partition pour le KTable Streams
    );

    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    /**
     * Méthode appelée automatiquement au démarrage de l'application CDI.
     * L'AdminClient fait des appels I/O synchrones, ce qui est acceptable
     * dans un observer @Observes StartupEvent (exécuté sur un thread dédié).
     */
    void onStart(@Observes StartupEvent event) {
        LOG.infof("🚀 Initialisation des topics Kafka sur %s ...", bootstrapServers);

        // ── Construction de l'AdminClient ─────────────────────────────────────
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000");
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "15000");

        try (AdminClient adminClient = AdminClient.create(props)) {
            createTopicsIfAbsent(adminClient);
            describeTopics(adminClient);
        } catch (Exception e) {
            // On loggue mais on ne bloque pas le démarrage :
            // les topics peuvent exister depuis un run précédent.
            LOG.warnf("Erreur lors de l'initialisation des topics : %s", e.getMessage());
        }
    }

    // ─── Création idempotente ──────────────────────────────────────────────────

    private void createTopicsIfAbsent(AdminClient adminClient) throws Exception {
        // 1. Lister les topics existants
        Set<String> existingTopics = adminClient.listTopics().names().get();
        LOG.infof("Topics existants : %s", existingTopics);

        // 2. Filtrer ceux qui sont déjà présents
        List<NewTopic> toCreate = new ArrayList<>();
        for (NewTopic topic : REQUIRED_TOPICS) {
            if (existingTopics.contains(topic.name())) {
                LOG.infof("  ✔ Topic '%s' déjà présent, skipping.", topic.name());
            } else {
                toCreate.add(topic);
                LOG.infof("  ➕ Topic '%s' à créer (%d partitions, réplication %d).",
                          topic.name(),
                          topic.numPartitions(),
                          topic.replicationFactor());
            }
        }

        if (toCreate.isEmpty()) {
            LOG.info("Tous les topics sont déjà présents. Rien à créer.");
            return;
        }

        // 3. Créer les topics manquants
        var createResult = adminClient.createTopics(
                toCreate,
                new CreateTopicsOptions().timeoutMs(10_000)
        );

        // 4. Attendre et gérer les résultats topic par topic
        for (NewTopic topic : toCreate) {
            try {
                createResult.values().get(topic.name()).get();  // bloquant jusqu'à ack
                LOG.infof("  ✅ Topic '%s' créé avec succès.", topic.name());
            } catch (ExecutionException ex) {
                if (ex.getCause() instanceof TopicExistsException) {
                    // Race condition : le topic a été créé entre le listTopics() et le createTopics()
                    LOG.infof("  ✔ Topic '%s' créé entre-temps (TopicExistsException ignorée).", topic.name());
                } else {
                    LOG.errorf("  ❌ Erreur création topic '%s' : %s", topic.name(), ex.getCause().getMessage());
                    throw ex;
                }
            }
        }
    }

    // ─── Description (log pédagogique) ────────────────────────────────────────

    private void describeTopics(AdminClient adminClient) throws Exception {
        // Récupère les noms de tous les topics Tuuuur pour les décrire
        List<String> tuuuurTopics = REQUIRED_TOPICS.stream()
                .map(NewTopic::name)
                .toList();

        var descriptions = adminClient.describeTopics(tuuuurTopics).allTopicNames().get();

        LOG.info("──────────────────────────────────────────────────────");
        LOG.info("📋 État final des topics Kafka – Tuuuur Merch Saga");
        LOG.info("──────────────────────────────────────────────────────");

        descriptions.forEach((name, desc) -> {
            LOG.infof("  %-25s | %d partition(s) | %d réplica(s)",
                      name,
                      desc.partitions().size(),
                      desc.partitions().get(0).replicas().size());
        });

        LOG.info("──────────────────────────────────────────────────────");
        LOG.infof("✅ Admin API : %d topics opérationnels.", descriptions.size());
    }
}
