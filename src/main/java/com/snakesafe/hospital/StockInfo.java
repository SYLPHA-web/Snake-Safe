package com.snakesafe.hospital;

import java.time.Instant;

public record StockInfo(int vials, StockStatus status, Instant updatedAt, Long minutesAgo) {
}
