package org.dobi.app.controller;

import org.dobi.app.service.ReportingService;
import org.dobi.dto.AlarmReportDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('ADMIN')") // Seuls les admins peuvent accéder aux rapports
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/alarms")
    public ResponseEntity<AlarmReportDto> getAlarmReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {

        AlarmReportDto report = reportingService.generateAlarmReport(start, end);
        return ResponseEntity.ok(report);
    }
}
