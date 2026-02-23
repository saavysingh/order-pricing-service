package com.example.orderservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreatedListener.class);

    @KafkaListener(topics = "order.created", groupId = "order-service-test")
    public void onMessage(String message) {
        logger.info("Received order.created: {}", message);
    }
}
