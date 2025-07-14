package org.dobi.dto;

import java.time.LocalDateTime;

public record PushSubscriptionDto(String endpoint, Keys keys) {

    public record Keys(String p256dh, String auth) {

    }
}
