package com.iotecomm.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/** Body sent by the ESP32 when the submit button is pressed. */
public record OrderRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String clientOrderId,
        @NotEmpty @Size(max = 16) List<@Valid Item> items) {

    public record Item(
            @NotBlank @Size(max = 32) String sku,
            @Min(0) @Max(5) int quantity) {}
}
