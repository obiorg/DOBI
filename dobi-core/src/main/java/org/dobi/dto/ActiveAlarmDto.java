package org.dobi.dto;

import java.time.LocalDateTime;

/**
 * DTO (Data Transfer Object) pour transporter les informations d'une alarme
 * active vers le frontend ou d'autres services.
 */
public record ActiveAlarmDto(
        Long activeAlarmId,
        String alarmName,
        String alarmDescription,
        String tagName,
        Float triggerValue,
        LocalDateTime triggerTime,
        boolean isAcknowledged,
        LocalDateTime ackTime,
        LocalDateTime resolvedTime,
        String severity, // Ex: "CRITICAL", "HIGH"
        String renderColor, // Ex: "255;255;255"
        String renderBackground, // Ex: "204;0;0"
        boolean renderBlink
        ) {

}
