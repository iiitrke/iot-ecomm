package com.iotecomm.service;

import com.iotecomm.api.dto.OrderRequest;
import com.iotecomm.config.IotEcommProperties;
import com.iotecomm.kafka.OrderEvent;
import com.iotecomm.kafka.OrderProducer;
import com.iotecomm.ledger.GitOrderLedger;
import com.iotecomm.ledger.OrderRecord;
import com.iotecomm.ledger.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final Map<String, IotEcommProperties.Product> catalog;
    private final Map<String, IotEcommProperties.Device> devices;
    private final OrderProducer producer;
    private final GitOrderLedger ledger;

    public OrderService(IotEcommProperties props, OrderProducer producer, GitOrderLedger ledger) {
        this.catalog = props.products().stream()
                .collect(Collectors.toMap(IotEcommProperties.Product::sku, Function.identity()));
        this.devices = props.devices().stream()
                .collect(Collectors.toMap(IotEcommProperties.Device::id, Function.identity()));
        this.producer = producer;
        this.ledger = ledger;
    }

    /**
     * Validates a device order, prices it from the catalogue, and publishes it to Kafka (over SSL).
     * The order id is derived from (deviceId, clientOrderId) so device retries are idempotent.
     */
    public OrderEvent accept(String deviceId, OrderRequest req) {
        Map<String, Integer> qtyBySku = new LinkedHashMap<>();
        for (var item : req.items()) {
            if (item.quantity() > 0) qtyBySku.merge(item.sku(), item.quantity(), Integer::sum);
        }
        if (qtyBySku.isEmpty()) throw new InvalidOrderException("Order has no items");

        List<OrderEvent.Item> items = new ArrayList<>();
        int total = 0;
        for (var e : qtyBySku.entrySet()) {
            var p = catalog.get(e.getKey());
            if (p == null) throw new InvalidOrderException("Unknown product: " + e.getKey());
            int qty = e.getValue();
            if (qty > 5) throw new InvalidOrderException("Quantity for " + p.sku() + " must be 1-5");
            items.add(new OrderEvent.Item(p.sku(), p.name(), qty, p.priceCents()));
            total += qty * p.priceCents();
        }

        UUID orderId = UUID.nameUUIDFromBytes((deviceId + "|" + req.clientOrderId()).getBytes(StandardCharsets.UTF_8));
        var event = new OrderEvent(orderId, deviceId, req.clientOrderId(), items, total, Instant.now());
        producer.publish(event);
        return event;
    }

    /** Kafka consumer side: write the order to the Markdown ledger and commit it. */
    public void record(OrderEvent ev) throws Exception {
        var device = devices.get(ev.deviceId());
        var order = new OrderRecord(ev.orderId(), ev.deviceId(), device != null ? device.customerName() : ev.deviceId(),
                ev.clientOrderId(), OrderStatus.PENDING, ev.totalCents(), ev.createdAt(), ev.createdAt(),
                ev.items().stream().map(i -> new OrderRecord.Item(i.sku(), i.name(), i.quantity(), i.unitPriceCents())).toList());
        if (!ledger.add(order)) {
            log.info("Order {} already recorded, skipping duplicate", ev.orderId());
        }
    }

    public long openOrderCount(String deviceId) {
        return ledger.openOrderCount(deviceId);
    }

    public List<OrderRecord> list(OrderStatus status) {
        return ledger.list(status);
    }

    public OrderRecord updateStatus(UUID id, OrderStatus status) throws Exception {
        return ledger.updateStatus(id, status);
    }

    public static class InvalidOrderException extends RuntimeException {
        public InvalidOrderException(String msg) { super(msg); }
    }
}
