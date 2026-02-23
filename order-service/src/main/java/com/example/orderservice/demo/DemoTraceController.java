package com.example.orderservice.demo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoTraceController {

    private final DemoTraceService demoTraceService;

    public DemoTraceController(DemoTraceService demoTraceService) {
        this.demoTraceService = demoTraceService;
    }

    @GetMapping("/trace")
    public ResponseEntity<?> trace(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) UUID eventId
    ) {
        if (orderId == null && eventId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Provide orderId or eventId"));
        }

        Optional<DemoTraceResponse> response = eventId != null
                ? demoTraceService.traceByEventId(eventId)
                : demoTraceService.traceByOrderId(orderId);

        if (response.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Order or event not found"));
        }

        return ResponseEntity.ok(response.get());
    }

    @GetMapping("/orders")
    public List<DemoOrderSummary> recentOrders(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return demoTraceService.recentOrders(limit);
    }

    @GetMapping("/recent")
    public List<DemoRecentOrder> recentOrdersDetailed(
            @RequestParam(defaultValue = "25") int limit
    ) {
        return demoTraceService.recentOrdersWithStatus(limit);
    }
}