package com.iotecomm.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderRecord(UUID id, String deviceId, String customerName, String clientOrderId, OrderStatus status,
                          int totalCents, Instant createdAt, Instant updatedAt, List<Item> items) {

    public record Item(String sku, String name, int quantity, int unitPriceCents) {}

    public OrderRecord withStatus(OrderStatus newStatus) {
        return new OrderRecord(id, deviceId, customerName, clientOrderId, newStatus, totalCents, createdAt,
                Instant.now(), items);
    }
}
