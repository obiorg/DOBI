package org.dobi.app.service;

import org.dobi.dto.MachineDetailDto;
import org.dobi.dto.MachineStatusDto;
import org.dobi.dto.TagDetailDto;
import org.dobi.dto.HistoryDataPointDto;
import org.dobi.entities.Machine;
import org.dobi.entities.PersStandard;
import org.dobi.entities.Tag;
import org.dobi.manager.MachineManagerService;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat; // <-- IMPORT MODIFIÉ
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SupervisionService {

    private final MachineManagerService machineManagerService;
    // CORRIGÉ : On utilise SimpleDateFormat pour les anciens objets Date
    private static final SimpleDateFormat HISTORY_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat LIVE_FORMATTER = new SimpleDateFormat("HH:mm:ss");

    public SupervisionService(MachineManagerService machineManagerService) {
        this.machineManagerService = machineManagerService;
    }

    public List<MachineStatusDto> getAllMachineStatuses() {
        return machineManagerService.getActiveCollectorDetails();
    }

    public void restartMachineCollector(Long id) {
        machineManagerService.restartCollector(id);
    }

    public MachineDetailDto getMachineDetails(Long machineId) {
        Machine machine = machineManagerService.getMachineFromDb(machineId);
        if (machine == null) {
            return null;
        }

        String currentStatus = machineManagerService.getActiveCollectorDetails().stream()
                .filter(s -> s.id() == machineId).map(MachineStatusDto::status).findFirst().orElse("Inconnu");

        List<TagDetailDto> tagDtos = (machine.getTags() != null) ? machine.getTags().stream()
                .map(this::toTagDetailDto).collect(Collectors.toList()) : Collections.emptyList();

        return new MachineDetailDto(machine.getId(), machine.getName(), currentStatus, tagDtos);
    }

    public TagDetailDto getTagDetails(Long tagId) {
        Tag tag = machineManagerService.getTagFromDb(tagId);
        return (tag != null) ? toTagDetailDto(tag) : null;
    }

    private TagDetailDto toTagDetailDto(Tag tag) {
        // CORRIGÉ : On utilise .format() sur le formatter, pas sur la date
        String formattedDate = (tag.getvStamp() != null) ? LIVE_FORMATTER.format(tag.getvStamp()) : "N/A";
        return new TagDetailDto(tag.getId(), tag.getName(), getLiveValue(tag), formattedDate);
    }

    private Object getLiveValue(Tag tag) {
        if (tag == null) {
            return "N/A";
        }
        if (tag.getvFloat() != null) {
            return tag.getvFloat();
        }
        if (tag.getvInt() != null) {
            return tag.getvInt();
        }
        if (tag.getvBool() != null) {
            return tag.getvBool();
        }
        if (tag.getvStr() != null) {
            return tag.getvStr();
        }
        if (tag.getvDateTime() != null) {
            return tag.getvDateTime();
        }
        return "N/A";
    }

    public List<HistoryDataPointDto> getTagHistory(Long tagId, int page, int size) {
        List<PersStandard> history = machineManagerService.getTagHistory(tagId, page, size);
        return history.stream().map(h -> {
            // CORRIGÉ : On utilise .format() sur le formatter, pas sur la date
            String timestamp = (h.getVStamp() != null) ? HISTORY_FORMATTER.format(h.getVStamp()) : "Date Inconnue";
            Object value = getHistoryValue(h);
            return new HistoryDataPointDto(timestamp, value);
        }).collect(Collectors.toList());
    }

    private Object getHistoryValue(PersStandard h) {
        if (h == null) {
            return "N/A";
        }
        if (h.getVFloat() != null) {
            return h.getVFloat();
        }
        if (h.getVInt() != null) {
            return h.getVInt();
        }
        if (h.getVBool() != null) {
            return h.getVBool();
        }
        if (h.getVStr() != null) {
            return h.getVStr();
        }
        if (h.getVDateTime() != null) {
            return h.getVDateTime();
        }
        return "N/A";
    }
}
