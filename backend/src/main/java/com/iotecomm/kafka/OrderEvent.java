package com.iotecomm.kafka;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Message published to Kafka for every order accepted from a device. Prices are snapshotted at order time. */
public record OrderEvent(
        UUID orderId,
        String deviceId,
        String clientOrderId,
        List<Item> items,
        int totalCents,
        Instant createdAt) {

    public record Item(String sku, String name, int quantity, int unitPriceCents) {}
}
