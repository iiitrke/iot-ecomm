package com.iotecomm.security;

import com.iotecomm.config.IotEcommProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Authenticates ESP32 devices via X-Device-Id + X-Api-Key headers against the configured device list. */
public class DeviceAuthFilter extends OncePerRequestFilter {
    public static final String DEVICE_ID_ATTR = "iotecomm.deviceId";
    private final Map<String, IotEcommProperties.Device> devices;

    public DeviceAuthFilter(List<IotEcommProperties.Device> devices) {
        this.devices = devices.stream().collect(Collectors.toMap(IotEcommProperties.Device::id, Function.identity()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String deviceId = req.getHeader("X-Device-Id");
        String apiKey = req.getHeader("X-Api-Key");
        var device = deviceId == null ? null : devices.get(deviceId);
        if (device == null || apiKey == null
                || !Hashing.constantTimeEquals(device.apiKeySha256().toLowerCase(), Hashing.sha256Hex(apiKey))) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid device credentials");
            return;
        }
        req.setAttribute(DEVICE_ID_ATTR, deviceId);
        chain.doFilter(req, res);
    }
}
