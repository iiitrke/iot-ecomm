package com.iotecomm.api;

import com.iotecomm.ledger.OrderRecord;
import com.iotecomm.ledger.OrderStatus;
import com.iotecomm.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Shop-side endpoints for viewing and fulfilling orders (X-Admin-Key required). */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderRecord> list(@RequestParam(required = false) OrderStatus status) {
        return orderService.list(status);
    }

    public record StatusUpdate(@NotNull OrderStatus status) {}

    /** e.g. {"status":"DELIVERED"} — commits the change to the ledger repo; the device's PENDING LED follows. */
    @PatchMapping("/{id}/status")
    public OrderRecord updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdate body) throws Exception {
        return orderService.updateStatus(id, body.status());
    }
}
