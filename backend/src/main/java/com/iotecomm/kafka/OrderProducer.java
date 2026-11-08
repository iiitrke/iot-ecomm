package com.iotecomm.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class OrderProducer {
    private final KafkaTemplate<String, OrderEvent> template;
    private final String topic;
    private final long timeoutSeconds;

    public OrderProducer(KafkaTemplate<String, OrderEvent> template,
                         @Value("${iotecomm.kafka.orders-topic}") String topic,
                         @Value("${iotecomm.kafka.send-timeout-seconds}") long timeoutSeconds) {
        this.template = template;
        this.topic = topic;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Publishes synchronously so the device only sees success once Kafka has durably
     * stored the order (acks=all). Keyed by deviceId to keep per-device ordering.
     */
    public void publish(OrderEvent event) {
        try {
            template.send(topic, event.deviceId(), event).get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrderPublishException("Interrupted while publishing order", e);
        } catch (Exception e) {
            throw new OrderPublishException("Failed to publish order to Kafka", e);
        }
    }

    public static class OrderPublishException extends RuntimeException {
        public OrderPublishException(String msg, Throwable cause) { super(msg, cause); }
    }
}
