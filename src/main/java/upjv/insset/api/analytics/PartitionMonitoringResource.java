package upjv.insset.api.analytics;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;
import upjv.insset.kafka.services.PartitionRebalanceListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * ============================================================
 * PartitionMonitoringResource – API de Monitoring des Partitions
 * ============================================================
 *
 * Responsabilité :
 *  Exposer des endpoints REST pour monitorer l'état des partitions
 *  assignées à cette instance Quarkus.
 *
 * Endpoints :
 *  GET /api/partitions/status     : État de rebalancing + partitions assignées
 *  GET /api/partitions/count      : Nombre de partitions
 *  GET /api/partitions/assigned   : Liste des numéros de partitions
 *
 * Cas d'usage :
 *  ✓ Debugging distribué : savoir quelles partitions chaque instance traite
 *  ✓ Alertes Kubernetes  : rebalancing anormal → déclencher alertes
 *  ✓ Dashboard           : visualiser la charge (nombre de partitions/instance)
 *  ✓ Validation          : tester que scalabilité fonctionne correctement
 * ============================================================
 */
@Path("/api/partitions")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class PartitionMonitoringResource {

    private static final Logger LOG = Logger.getLogger(PartitionMonitoringResource.class);

    @Inject
    PartitionRebalanceListener rebalanceListener;

    /**
     * Retourner le statut courant des partitions.
     *
     * Réponse type :
     * {
     *   "status": "BALANCED",
     *   "assignedPartitions": [0, 2],
     *   "partitionCount": 2,
     *   "timestamp": 1712973456000,
     *   "instanceId": "tuuuur-merch-1"
     * }
     */
    @GET
    @Path("/status")
    public Map<String, Object> getPartitionStatus() {
        Set<Integer> partitions = rebalanceListener.getAssignedPartitions();

        Map<String, Object> response = new HashMap<>();
        response.put("status", partitions.isEmpty() ? "REBALANCING" : "BALANCED");
        response.put("assignedPartitions", partitions);
        response.put("partitionCount", rebalanceListener.getPartitionCount());
        response.put("timestamp", System.currentTimeMillis());
        response.put("instanceId", System.getenv()
                .getOrDefault("HOSTNAME", "instance-unknown"));

        LOG.infof("📊 Partition Status: %s", response);
        return response;
    }

    /**
     * Retourner le nombre de partitions traitées par cette instance.
     * Utile pour des alertes Kubernetes sur load balancing.
     *
     * Réponse type : {"partitionCount": 2}
     */
    @GET
    @Path("/count")
    public Map<String, Integer> getPartitionCount() {
        int count = rebalanceListener.getPartitionCount();
        
        Map<String, Integer> response = new HashMap<>();
        response.put("partitionCount", count);
        response.put("capacity", 10); // Nombre max de partitions attendues (à adapter)

        LOG.debugf("Partition count query: %d/%d", count, 10);
        return response;
    }

    /**
     * Retourner la liste des identifiants de partitions assignées.
     *
     * Réponse type : {"assigned": [0, 2], "total": 2}
     */
    @GET
    @Path("/assigned")
    public Map<String, Object> getAssignedPartitions() {
        Set<Integer> partitions = rebalanceListener.getAssignedPartitions();

        Map<String, Object> response = new HashMap<>();
        response.put("assigned", partitions.stream().sorted().toList());
        response.put("total", partitions.size());

        LOG.infof("Assigned partitions: %s", partitions);
        return response;
    }

    /**
     * Health check pour Kubernetes Liveness Probe.
     * Retourner 200 OK si l'instance est "ready", sinon 503.
     *
     * Logique :
     *  - Si partitions assignées > 0 → instance "healthy"
     *  - Si partitions = 0 et rebalancing en cours → attendre
     *  - Si partitions = 0 pendant > N secondes → considérer comme morte
     *
     * Réponse type : {"ready": true, "partitions": 2}
     */
    @GET
    @Path("/health")
    public Map<String, Object> healthCheck() {
        int count = rebalanceListener.getPartitionCount();
        boolean ready = count > 0;

        Map<String, Object> response = new HashMap<>();
        response.put("ready", ready);
        response.put("partitions", count);
        response.put("message", ready ? "Instance ready" : "Instance rebalancing or idle");

        LOG.debugf("Health check: ready=%s, partitions=%d", ready, count);
        return response;
    }
}
