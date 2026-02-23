package com.example.orderservice;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private static final String TOPIC = "order.created";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final long baseBackoffMs;
    private final long maxBackoffMs;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${outbox.publisher.batch-size:50}") int batchSize,
            @Value("${outbox.publisher.base-backoff-ms:500}") long baseBackoffMs,
            @Value("${outbox.publisher.max-backoff-ms:30000}") long maxBackoffMs
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:1000}")
    @Transactional
    public void publishDueEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> events = outboxEventRepository.findDueForPublish(now, batchSize);
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(TOPIC, event.getEventId().toString(), event.getPayload()).get();
                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                outboxEventRepository.save(event);
            } catch (Exception ex) {
                int attempts = event.getAttempts() == null ? 0 : event.getAttempts();
                attempts += 1;
                event.setAttempts(attempts);
                event.setStatus("FAILED");
                event.setLastError(truncate(ex.getMessage()));
                event.setNextAttemptAt(Instant.now().plusMillis(backoffMs(attempts)));
                outboxEventRepository.save(event);
            }
        }
    }

    private long backoffMs(int attempts) {
        long backoff = baseBackoffMs * (1L << Math.min(attempts - 1, 10));
        return Math.min(backoff, maxBackoffMs);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
