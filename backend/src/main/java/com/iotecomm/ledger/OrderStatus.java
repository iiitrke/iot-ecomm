package com.iotecomm.ledger;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED;

    /** Orders in these states light the device's PENDING LED. */
    public static final Set<OrderStatus> OPEN = EnumSet.of(PENDING, CONFIRMED, SHIPPED);
}
