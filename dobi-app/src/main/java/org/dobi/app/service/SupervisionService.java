package org.dobi.app.service;

import org.dobi.dto.MachineDetailDto;
import org.dobi.dto.MachineStatusDto;
import org.dobi.dto.TagDetailDto;
import org.dobi.dto.HistoryDataPointDto;
import org.dobi.entities.PersStandard;
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
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public SupervisionService(MachineManagerService machineManagerService) { this.machineManagerService = machineManagerService; }
    public List<MachineStatusDto> getAllMachineStatuses() { return machineManagerService.getActiveCollectorDetails(); }
    public void restartMachineCollector(Long id) { machineManagerService.restartCollector(id); }

    public MachineDetailDto getMachineDetails(Long machineId) {
        Machine machine = machineManagerService.getMachineFromDb(machineId);
        if (machine == null) return null;
        
        String currentStatus = machineManagerService.getActiveCollectorDetails().stream()
            .filter(s -> s.id() == machineId)
            .map(MachineStatusDto::status)
            .findFirst()
            .orElse("Inconnu");
        
        List<TagDetailDto> tagDtos = (machine.getTags() != null) ? machine.getTags().stream()
            .map(this::toTagDetailDto)
            .collect(Collectors.toList()) : Collections.emptyList();
        
        return new MachineDetailDto(machine.getId(), machine.getName(), currentStatus, tagDtos);
    }

    private TagDetailDto toTagDetailDto(Tag tag) {
        return new TagDetailDto(
            tag.getId(),
            tag.getName(),
            getLiveValue(tag),
            tag.getvStamp() != null ? tag.getvStamp().format(FORMATTER) : "N/A"
        );
    }

    private Object getLiveValue(Tag tag) {
        if (tag.getvFloat() != null) return tag.getvFloat();
        if (tag.getvInt() != null) return tag.getvInt();
        if (tag.getvBool() != null) return tag.getvBool();
        if (tag.getvStr() != null) return tag.getvStr();
        if (tag.getvDateTime() != null) return tag.getvDateTime();
        return "N/A";
    }


    public List<HistoryDataPointDto> getTagHistory(Long tagId) {
        List<PersStandard> history = machineManagerService.getTagHistory(tagId);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return history.stream()
            .map(h -> new HistoryDataPointDto(
                h.getvStamp().format(formatter),
                getHistoryValue(h)
            ))
            .collect(java.util.stream.Collectors.toList());
    }
    
    private Object getHistoryValue(PersStandard history) {
        if (history.getvFloat() != null) return history.getvFloat();
        if (history.getvInt() != null) return history.getvInt();
        if (history.getvBool() != null) return history.getvBool();
        if (history.getvStr() != null) return history.getvStr();
        if (history.getvDateTime() != null) return history.getvDateTime();
        return "N/A";
    }
}

