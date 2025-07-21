package org.dobi.app.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import org.dobi.dto.AlarmFrequencyDto;
import org.dobi.dto.AlarmReportDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportingService {

    private final EntityManagerFactory emf;

    public ReportingService(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public AlarmReportDto generateAlarmReport(OffsetDateTime start, OffsetDateTime end) {
        EntityManager em = emf.createEntityManager();
        try {
            // Requête pour obtenir les statistiques générales et le temps d'acquittement moyen
            Query summaryQuery = em.createNativeQuery(
                    "SELECT "
                    + "  COUNT(*), "
                    + "  AVG(CAST(DATEDIFF(SECOND, trigger_time, ack_time) AS FLOAT)) "
                    + "FROM active_alarms "
                    + "WHERE trigger_time >= :start AND trigger_time <= :end AND ack_time IS NOT NULL"
            );
            summaryQuery.setParameter("start", Timestamp.from(start.toInstant()));
            summaryQuery.setParameter("end", Timestamp.from(end.toInstant()));
            Object[] summaryResult = (Object[]) summaryQuery.getSingleResult();

            long totalAlarms = (summaryResult[0] != null) ? ((Number) summaryResult[0]).longValue() : 0;
            double avgAckTime = (summaryResult[1] != null) ? ((Number) summaryResult[1]).doubleValue() : 0.0;

            // Requête pour obtenir les alarmes les plus fréquentes
            Query frequencyQuery = em.createNativeQuery(
                    "SELECT TOP 10 ad.name, COUNT(aa.id) as alarm_count "
                    + "FROM active_alarms aa "
                    + "JOIN alarms ad ON aa.alarm_definition_id = ad.id "
                    + "WHERE aa.trigger_time >= :start AND aa.trigger_time <= :end "
                    + "GROUP BY ad.name "
                    + "ORDER BY alarm_count DESC"
            );
            frequencyQuery.setParameter("start", Timestamp.from(start.toInstant()));
            frequencyQuery.setParameter("end", Timestamp.from(end.toInstant()));

            @SuppressWarnings("unchecked")
            List<Object[]> frequencyResults = frequencyQuery.getResultList();
            List<AlarmFrequencyDto> alarmsByFrequency = frequencyResults.stream()
                    .map(row -> new AlarmFrequencyDto((String) row[0], ((Number) row[1]).longValue()))
                    .collect(Collectors.toList());

            return new AlarmReportDto(totalAlarms, avgAckTime, alarmsByFrequency);

        } finally {
            em.close();
        }
    }
}
