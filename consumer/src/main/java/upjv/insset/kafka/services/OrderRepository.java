package upjv.insset.kafka.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stockage en mémoire des commandes.
 *
 * ConcurrentHashMap garantit la thread-safety face aux appels
 * simultanés de l'endpoint REST (thread Vert.x) et des consumers Kafka
 * (threads du pool SmallRye).
 *
 * Note pédagogique : en production, ce serait remplacé par une base
 * de données avec un pattern Outbox pour garantir l'atomicité entre
 * la persistance et la publication Kafka.
 */
@ApplicationScoped
public class OrderRepository {

    private static final Logger LOG = Logger.getLogger(OrderRepository.class);

    private final ConcurrentHashMap<String, Order> store = new ConcurrentHashMap<>();

    public void save(Order order) {
        store.put(order.orderId, order);
        LOG.debugf("Order sauvegardée : %s", order);
    }

    public Order findById(String orderId) {
        return store.get(orderId);
    }

    public Collection<Order> findAll() {
        return Collections.unmodifiableCollection(store.values());
    }

    public void updateStatus(String orderId, OrderStatus status) {
        Order order = store.get(orderId);
        if (order != null) {
            order.updateStatus(status);
            LOG.infof("Order '%s' → %s", orderId, status);
        } else {
            LOG.warnf("updateStatus appelé sur orderId inconnu : %s", orderId);
        }
    }

    public void setTransactionId(String orderId, String txId) {
        Order order = store.get(orderId);
        if (order != null) {
            order.transactionId = txId;
        }
    }

    public int count() {
        return store.size();
    }
}
