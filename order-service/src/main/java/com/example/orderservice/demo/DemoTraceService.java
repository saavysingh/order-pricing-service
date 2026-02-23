package com.example.orderservice.demo;

import com.example.orderservice.Order;
import com.example.orderservice.OrderRepository;
import com.example.orderservice.OutboxEvent;
import com.example.orderservice.OutboxEventRepository;
import com.example.orderservice.demo.DemoTraceResponse.DlqTrace;
import com.example.orderservice.demo.DemoTraceResponse.OutboxTrace;
import com.example.orderservice.demo.DemoTraceResponse.PricingTrace;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class DemoTraceService {

    private static final String EVENT_TYPE_ORDER_CREATED = "ORDER_CREATED";

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JdbcTemplate jdbcTemplate;

    public DemoTraceService(OrderRepository orderRepository, OutboxEventRepository outboxEventRepository, JdbcTemplate jdbcTemplate) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DemoTraceResponse> traceByOrderId(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return Optional.empty();
        }

        OutboxEvent outboxEvent = outboxEventRepository
                .findTopByAggregateIdAndEventTypeOrderByCreatedAtDesc(orderId, EVENT_TYPE_ORDER_CREATED)
                .orElse(null);

        UUID eventId = outboxEvent != null ? outboxEvent.getEventId() : null;
        PricingResult pricingResult = findPricingResult(orderId);
        ProcessedEvent processedEvent = eventId != null ? findProcessedEvent(eventId) : null;
        DlqEvent dlqEvent = eventId != null ? findDlqEvent(eventId) : null;

        return Optional.of(buildResponse(order, eventId, outboxEvent, pricingResult, processedEvent, dlqEvent));
    }

    public Optional<DemoTraceResponse> traceByEventId(UUID eventId) {
        OutboxEvent outboxEvent = outboxEventRepository.findById(eventId).orElse(null);
        UUID orderId = outboxEvent != null ? outboxEvent.getAggregateId() : null;

        ProcessedEvent processedEvent = findProcessedEvent(eventId);
        if (orderId == null && processedEvent != null) {
            orderId = processedEvent.orderId();
        }

        if (orderId == null) {
            return Optional.empty();
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return Optional.empty();
        }

        PricingResult pricingResult = findPricingResult(orderId);
        DlqEvent dlqEvent = findDlqEvent(eventId);

        return Optional.of(buildResponse(order, eventId, outboxEvent, pricingResult, processedEvent, dlqEvent));
    }

    public List<DemoOrderSummary> recentOrders(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return jdbcTemplate.query(
                "select order_id, customer_id, currency, subtotal_cents, status, created_at " +
                        "from orders order by created_at desc limit ?",
                (rs, rowNum) -> new DemoOrderSummary(
                        UUID.fromString(rs.getString("order_id")),
                        rs.getString("customer_id"),
                        rs.getString("currency"),
                        rs.getInt("subtotal_cents"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                safeLimit
        );
    }

            public List<DemoRecentOrder> recentOrdersWithStatus(int limit) {
            int safeLimit = Math.min(Math.max(limit, 1), 200);
            return jdbcTemplate.query(
                "select o.order_id, o.subtotal_cents, o.created_at, " +
                    "pr.final_price_cents, " +
                    "case when de.event_id is not null then true else false end as has_dlq " +
                    "from orders o " +
                    "left join pricing_results pr on pr.order_id = o.order_id " +
                    "left join dlq_events de on de.order_id = o.order_id " +
                    "order by o.created_at desc limit ?",
                (rs, rowNum) -> {
                    boolean hasDlq = rs.getBoolean("has_dlq");
                    Integer finalPriceCents = (Integer) rs.getObject("final_price_cents");
                    String pricingStatus = hasDlq ? "FAILED" : (finalPriceCents != null ? "PRICED" : "PENDING");
                    return new DemoRecentOrder(
                        UUID.fromString(rs.getString("order_id")),
                        rs.getTimestamp("created_at").toInstant(),
                        pricingStatus,
                        rs.getInt("subtotal_cents"),
                        finalPriceCents
                    );
                },
                safeLimit
            );
            }

    private DemoTraceResponse buildResponse(Order order, UUID eventId, OutboxEvent outboxEvent, PricingResult pricingResult, ProcessedEvent processedEvent, DlqEvent dlqEvent) {
        OutboxTrace outboxTrace = null;
        if (outboxEvent != null) {
            outboxTrace = new OutboxTrace(
                    outboxEvent.getStatus(),
                    outboxEvent.getAttempts(),
                    outboxEvent.getCreatedAt(),
                    outboxEvent.getPublishedAt(),
                    outboxEvent.getNextAttemptAt(),
                    outboxEvent.getLastError()
            );
        }

        PricingTrace pricingTrace = new PricingTrace(
                pricingResult != null,
                pricingResult != null ? pricingResult.computedAt() : null,
                pricingResult != null ? pricingResult.taxCents() : null,
                pricingResult != null ? pricingResult.discountCents() : null,
                processedEvent != null,
                processedEvent != null ? processedEvent.processedAt() : null
        );

        DlqTrace dlqTrace = dlqEvent != null
            ? new DlqTrace("DLQ", dlqEvent.attempts(), dlqEvent.error(), dlqEvent.createdAt())
            : new DlqTrace("NONE", null, null, null);
        String pricingStatus = dlqEvent != null ? "FAILED" : (pricingResult != null ? "PRICED" : "PENDING");

        return new DemoTraceResponse(
                order.getOrderId(),
                eventId,
                true,
                order.getStatus(),
                pricingStatus,
                order.getSubtotalCents(),
                pricingResult != null ? pricingResult.finalPriceCents() : null,
                outboxTrace,
                pricingTrace,
                dlqTrace
        );
    }

    private PricingResult findPricingResult(UUID orderId) {
        try {
            return jdbcTemplate.queryForObject(
                    "select final_price_cents, tax_cents, discount_cents, computed_at from pricing_results where order_id = ?",
                    new PricingResultMapper(),
                    orderId
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private ProcessedEvent findProcessedEvent(UUID eventId) {
        try {
            return jdbcTemplate.queryForObject(
                    "select event_id, order_id, processed_at from processed_events where event_id = ?",
                    new ProcessedEventMapper(),
                    eventId
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private DlqEvent findDlqEvent(UUID eventId) {
        try {
            return jdbcTemplate.queryForObject(
                    "select event_id, order_id, attempts, error, created_at from dlq_events where event_id = ?",
                    new DlqEventMapper(),
                    eventId
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private record PricingResult(Integer finalPriceCents, Integer taxCents, Integer discountCents, Instant computedAt) {
    }

    private record ProcessedEvent(UUID eventId, UUID orderId, Instant processedAt) {
    }

    private record DlqEvent(UUID eventId, UUID orderId, Integer attempts, String error, Instant createdAt) {
    }

    private static final class PricingResultMapper implements RowMapper<PricingResult> {
        @Override
        public PricingResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new PricingResult(
                    rs.getInt("final_price_cents"),
                    rs.getInt("tax_cents"),
                    rs.getInt("discount_cents"),
                    rs.getTimestamp("computed_at").toInstant()
            );
        }
    }

    private static final class ProcessedEventMapper implements RowMapper<ProcessedEvent> {
        @Override
        public ProcessedEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ProcessedEvent(
                    UUID.fromString(rs.getString("event_id")),
                    UUID.fromString(rs.getString("order_id")),
                    rs.getTimestamp("processed_at").toInstant()
            );
        }
    }

    private static final class DlqEventMapper implements RowMapper<DlqEvent> {
        @Override
        public DlqEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DlqEvent(
                    UUID.fromString(rs.getString("event_id")),
                    UUID.fromString(rs.getString("order_id")),
                    rs.getInt("attempts"),
                    rs.getString("error"),
                    rs.getTimestamp("created_at").toInstant()
            );
        }
    }
}