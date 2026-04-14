package upjv.insset.kafka.services;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.common.TopicPartition;
import org.jboss.logging.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ============================================================
 * ConsumerLagMetricsService
 * ============================================================
 *
 * Responsabilité:
 *   Collecter les offsets des consumer groups et calculer le lag.
 *   Le lag = (end offset - current offset) pour chaque partition.
 *   Exporte les métriques vers Prometheus via MeterRegistry.
 *
 * Exécution:
 *   - Scheduled: toutes les 5 minutes (évite la surcharge de requêtes)
 *   - Metrics exportées:
 *     * tuuuur.kafka.consumer.lag (gauge)
 *     * tuuuur.kafka.consumer.max_lag (gauge)
 * ============================================================
 */
@ApplicationScoped
public class ConsumerLagMetricsService {

    private static final Logger LOG = Logger.getLogger(ConsumerLagMetricsService.class);

    @Inject
    MeterRegistry meterRegistry;

    private AdminClient adminClient;
    private ScheduledExecutorService scheduler;

    // List of consumer groups to monitor
    private static final String[] CONSUMER_GROUPS = {
        "order-service-payment-grp",
        "gamesync-service-grp",
        "stock-service-grp",
        "payment-service-grp",
        "order-service-rank-observer",
        "order-service-stock-observer",
        "orders-partition-processor-grp"
    };

    void onStart(@Observes StartupEvent ev) {
        try {
            Properties props = new Properties();
            // Use internal Docker network hostnames for Kafka brokers
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, 
                "kafka-broker-1:29092,kafka-broker-2:29092,kafka-broker-3:29092");
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
            this.adminClient = AdminClient.create(props);
            
            // Schedule periodic lag collection every 5 minutes
            this.scheduler = new ScheduledThreadPoolExecutor(1);
            scheduler.scheduleAtFixedRate(
                this::collectConsumerLagMetrics,
                1, // Initial delay of 1 minute
                5, // Then every 5 minutes
                TimeUnit.MINUTES
            );
            
            LOG.info("✅ ConsumerLagMetricsService initialized - lag collection scheduled");
        } catch (Exception e) {
            LOG.errorf("❌ Failed to initialize AdminClient: %s", e.getMessage());
        }
    }

    /**
     * Periodic task: collect consumer lag metrics
     */
    public void collectConsumerLagMetrics() {
        if (adminClient == null) {
            LOG.warn("⚠️ AdminClient not initialized, skipping lag collection");
            return;
        }

        try {
            for (String groupId : CONSUMER_GROUPS) {
                computeGroupLag(groupId);
            }
            LOG.debugf("✅ Consumer lag metrics collected successfully");
        } catch (Exception e) {
            LOG.errorf("❌ Error collecting consumer lag metrics: %s", e.getMessage());
        }
    }

    /**
     * Compute lag for a specific consumer group
     */
    private void computeGroupLag(String groupId) throws ExecutionException, InterruptedException {
        try {
            // Get consumer group description
            ConsumerGroupDescription groupDesc = adminClient.describeConsumerGroups(
                Collections.singletonList(groupId)
            ).describedGroups().get(groupId).get();

            // Get current offsets for this group
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets =
                offsetsResult.partitionsToOffsetAndMetadata().get();

            if (offsets.isEmpty()) {
                LOG.debugf("⚠️ No offsets found for consumer group: %s", groupId);
                return;
            }

            // Get end offsets for all partitions in this group
            Map<TopicPartition, OffsetSpec> offsetSpecs = new HashMap<>();
            for (TopicPartition tp : offsets.keySet()) {
                offsetSpecs.put(tp, OffsetSpec.latest());
            }

            ListOffsetsResult endOffsetsResult = adminClient.listOffsets(offsetSpecs);
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsetsMap = 
                endOffsetsResult.all().get();

            // Calculate lag for each partition
            long totalLag = 0;
            long maxPartitionLag = 0;

            for (TopicPartition tp : offsets.keySet()) {
                long currentOffset = offsets.get(tp).offset();
                long endOffset = endOffsetsMap.get(tp).offset();
                long partitionLag = Math.max(0, endOffset - currentOffset);

                totalLag += partitionLag;
                maxPartitionLag = Math.max(maxPartitionLag, partitionLag);

                // Store lag in AtomicLong for gauge
                final AtomicLong partitionLagValue = new AtomicLong(partitionLag);
                final String finalTopic = tp.topic();
                final int finalPartition = tp.partition();

                // Publish per-partition lag gauge
                meterRegistry.gauge(
                    "tuuuur.kafka.consumer.partition.lag",
                    Tags.of(
                        "consumer_group", groupId,
                        "topic", finalTopic,
                        "partition", String.valueOf(finalPartition)
                    ),
                    partitionLagValue,
                    AtomicLong::get
                );

                if (partitionLag > 100) {
                    LOG.warnf("⚠️ High lag detected - Group: %s, Topic: %s, Partition: %d, Lag: %d",
                        groupId, finalTopic, finalPartition, partitionLag);
                }
            }

            // Publish total lag for the group
            final AtomicLong totalLagValue = new AtomicLong(totalLag);
            meterRegistry.gauge(
                "tuuuur.kafka.consumer.total.lag",
                Tags.of("consumer_group", groupId),
                totalLagValue,
                AtomicLong::get
            );

            // Publish max lag for the group
            final AtomicLong maxPartitionLagValue = new AtomicLong(maxPartitionLag);
            meterRegistry.gauge(
                "tuuuur.kafka.consumer.max.lag",
                Tags.of("consumer_group", groupId),
                maxPartitionLagValue,
                AtomicLong::get
            );

            LOG.debugf("📊 Group %s - Total Lag: %d, Max Partition Lag: %d", groupId, totalLag, maxPartitionLag);
        } catch (Exception e) {
            LOG.debugf("⚠️ Could not compute lag for group %s: %s", groupId, e.getMessage());
        }
    }

    /**
     * Gracefully close AdminClient on shutdown
     */
    void onStop(@Observes io.quarkus.runtime.ShutdownEvent ev) {
        if (scheduler != null) {
            scheduler.shutdown();
            LOG.info("✅ Scheduler shut down");
        }
        if (adminClient != null) {
            adminClient.close();
            LOG.info("✅ AdminClient closed");
        }
    }
}
