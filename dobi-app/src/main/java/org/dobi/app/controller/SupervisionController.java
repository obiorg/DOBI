package org.dobi.app.controller;

import org.dobi.dto.MachineDetailDto;
import org.dobi.dto.MachineStatusDto;
import org.dobi.dto.TagDetailDto;
import org.dobi.dto.HistoryDataPointDto;
import org.dobi.app.service.SupervisionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SupervisionController {
    private final SupervisionService supervisionService;
    public SupervisionController(SupervisionService supervisionService) { this.supervisionService = supervisionService; }
    
    @GetMapping("/status")
    public Map<String, String> getApiStatus() { return Map.of("status", "OK"); }
    
    @GetMapping("/machines")
    public List<MachineStatusDto> getAllMachineStatuses() { return supervisionService.getAllMachineStatuses(); }
    
    @GetMapping("/machines/{id}")
    public ResponseEntity<MachineDetailDto> getMachineDetails(@PathVariable Long id) {
        MachineDetailDto details = supervisionService.getMachineDetails(id);
        return (details != null) ? ResponseEntity.ok(details) : ResponseEntity.notFound().build();
    }

    @PostMapping("/machines/{id}/restart")
    public ResponseEntity<Void> restartMachine(@PathVariable Long id) {
        supervisionService.restartMachineCollector(id);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/tags/{id}")
    public ResponseEntity<TagDetailDto> getTagDetails(@PathVariable Long id) {
        TagDetailDto details = supervisionService.getTagDetails(id);
        return (details != null) ? ResponseEntity.ok(details) : ResponseEntity.notFound().build();
    }

    @GetMapping("/tags/{id}/history")
    public List<HistoryDataPointDto> getTagHistory(
            @PathVariable Long id, 
            @RequestParam(defaultValue = "0") int page, 
            @RequestParam(defaultValue = "100") int size) {
        return supervisionService.getTagHistory(id, page, size);
    }
}
