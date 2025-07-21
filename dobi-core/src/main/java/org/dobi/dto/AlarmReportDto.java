package org.dobi.dto;

import java.util.List;

// DTO principal pour le rapport d'alarmes
public record AlarmReportDto(
        long totalAlarms,
        double averageAckTimeSeconds, // Temps moyen d'acquittement en secondes
        List<AlarmFrequencyDto> alarmsByFrequency
        ) {

}
