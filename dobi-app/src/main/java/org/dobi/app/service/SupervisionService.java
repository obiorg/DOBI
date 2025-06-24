package org.dobi.app.service;

import org.dobi.app.dto.MachineDetailDto;
import org.dobi.app.dto.TagDetailDto;
import org.dobi.dto.MachineStatusDto;
import org.dobi.entities.Machine;
import org.dobi.entities.Tag;
import org.dobi.manager.MachineManagerService;
import org.springframework.stereotype.Service;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SupervisionService {
    private final MachineManagerService machineManagerService;
    public SupervisionService(MachineManagerService machineManagerService) { this.machineManagerService = machineManagerService; }
    public List<MachineStatusDto> getAllMachineStatuses() { return machineManagerService.getActiveCollectorDetails(); }
    public void restartMachineCollector(Long id) { machineManagerService.restartCollector(id); }

    public MachineDetailDto getMachineDetails(Long machineId) {
        Machine machine = machineManagerService.getMachineFromDb(machineId);
        if (machine == null) return null;
        
        MachineStatusDto statusDto = machineManagerService.getActiveCollectorDetails().stream()
            .filter(s -> s.id() == machineId).findFirst().orElse(null);
        
        String currentStatus = (statusDto != null) ? statusDto.status() : "Inconnu";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        List<TagDetailDto> tagDtos = (machine.getTags() != null) ? machine.getTags().stream()
            .map(tag -> new TagDetailDto(
                tag.getId(),
                tag.getName(),
                getLiveValue(tag),
                tag.getvStamp() != null ? tag.getvStamp().format(formatter) : "N/A"
            ))
            .collect(Collectors.toList()) : Collections.emptyList();
        
        return new MachineDetailDto(machine.getId(), machine.getName(), currentStatus, tagDtos);
    }

    private Object getLiveValue(Tag tag) {
        if (tag.getvFloat() != null) return tag.getvFloat();
        if (tag.getvInt() != null) return tag.getvInt();
        if (tag.getvBool() != null) return tag.getvBool();
        if (tag.getvStr() != null) return tag.getvStr();
        if (tag.getvDateTime() != null) return tag.getvDateTime();
        return "N/A";
    }
}
