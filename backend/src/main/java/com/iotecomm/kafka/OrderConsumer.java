package com.iotecomm.kafka;

import com.iotecomm.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);
    private final OrderService orderService;

    public OrderConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "${iotecomm.kafka.orders-topic}")
    public void onOrder(OrderEvent event) throws Exception {
        log.info("Received order {} from device {} ({} items)", event.orderId(), event.deviceId(), event.items().size());
        orderService.record(event);
    }
}
