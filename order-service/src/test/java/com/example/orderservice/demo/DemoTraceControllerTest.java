package com.example.orderservice.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orderservice.demo.DemoTraceResponse.DlqTrace;
import com.example.orderservice.demo.DemoTraceResponse.OutboxTrace;
import com.example.orderservice.demo.DemoTraceResponse.PricingTrace;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DemoTraceController.class)
@Import(DemoTraceControllerTest.TestConfig.class)
class DemoTraceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoTraceServiceStub demoTraceService;

    @Test
    void traceByOrderIdReturnsTrace() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        DemoTraceResponse response = new DemoTraceResponse(
                orderId,
                eventId,
                true,
                "CREATED",
                "PRICED",
                5200,
                5616,
                new OutboxTrace("PUBLISHED", 0, Instant.now(), Instant.now(), null, null),
                new PricingTrace(true, Instant.now(), 416, 0, true, Instant.now()),
                new DlqTrace("NONE", null, null, null)
        );

        demoTraceService.setTraceByOrderId(Optional.of(response));

        mockMvc.perform(get("/demo/trace").param("orderId", orderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.orderCreated").value(true))
                .andExpect(jsonPath("$.outbox.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.pricing.hasPricingResult").value(true));
    }

    @Test
    void traceReturnsNotFoundWhenMissing() throws Exception {
        demoTraceService.setTraceByOrderId(Optional.empty());

        mockMvc.perform(get("/demo/trace").param("orderId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void recentReturnsList() throws Exception {
        DemoRecentOrder recent = new DemoRecentOrder(UUID.randomUUID(), Instant.now(), "PENDING", 5200, null);
        demoTraceService.setRecentOrders(List.of(recent));

        mockMvc.perform(get("/demo/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pricingStatus").value("PENDING"));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        DemoTraceServiceStub demoTraceServiceStub() {
            return new DemoTraceServiceStub();
        }
    }

    static class DemoTraceServiceStub extends DemoTraceService {

        private Optional<DemoTraceResponse> traceByOrderId = Optional.empty();
        private Optional<DemoTraceResponse> traceByEventId = Optional.empty();
        private List<DemoRecentOrder> recentOrders = new ArrayList<>();

        DemoTraceServiceStub() {
            super(null, null, null);
        }

        void setTraceByOrderId(Optional<DemoTraceResponse> trace) {
            this.traceByOrderId = trace;
        }

        void setTraceByEventId(Optional<DemoTraceResponse> trace) {
            this.traceByEventId = trace;
        }

        void setRecentOrders(List<DemoRecentOrder> recentOrders) {
            this.recentOrders = recentOrders;
        }

        @Override
        public Optional<DemoTraceResponse> traceByOrderId(UUID orderId) {
            return traceByOrderId;
        }

        @Override
        public Optional<DemoTraceResponse> traceByEventId(UUID eventId) {
            return traceByEventId;
        }

        @Override
        public List<DemoRecentOrder> recentOrdersWithStatus(int limit) {
            return recentOrders;
        }
    }
}