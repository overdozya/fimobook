package com.fimobook.backend;

import java.time.LocalDateTime;

public record PriceRefreshCandidate(
        long cid,
        long pid,
        boolean tradeable,
        LocalDateTime priceCheckedAt) {
}
