package org.dobi.dto; import java.util.List; public record MachineDetailDto(long id, String name, String status, List<TagDetailDto> tags) {}
