package com.iotecomm.api;

import com.iotecomm.api.dto.OrderRequest;
import com.iotecomm.security.DeviceAuthFilter;
import com.iotecomm.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Endpoints called by the ESP32 (authenticated by DeviceAuthFilter). */
@RestController
@RequestMapping("/api/device")
public class DeviceOrderController {
    private final OrderService orderService;

    public DeviceOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> submit(@RequestAttribute(DeviceAuthFilter.DEVICE_ID_ATTR) String deviceId,
                                      @Valid @RequestBody OrderRequest request) {
        var event = orderService.accept(deviceId, request);
        return Map.of("orderId", event.orderId(), "status", "ACCEPTED", "totalCents", event.totalCents());
    }

    @GetMapping("/orders/pending")
    public Map<String, Object> pending(@RequestAttribute(DeviceAuthFilter.DEVICE_ID_ATTR) String deviceId) {
        return Map.of("pendingCount", orderService.openOrderCount(deviceId));
    }
}
