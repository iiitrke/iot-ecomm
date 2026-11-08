package com.iotecomm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "iotecomm")
public record IotEcommProperties(
        String adminApiKey,
        String currency,
        Ledger ledger,
        List<Product> products,
        List<Device> devices) {

    public record Ledger(String dir, String branch, String remoteUrl, String username, String token,
                         String authorName, String authorEmail) {}

    public record Product(String sku, String name, int priceCents) {}

    public record Device(String id, String customerName, String address, String apiKeySha256) {}
}
