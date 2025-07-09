package org.dobi.dto;

import java.time.LocalDateTime;

public record MachineStateDto(
        long id,
        String name,
        String status,
        long tagsReadCount,
        LocalDateTime connectedSince, // Heure du dernier démarrage/connexion
        int connectionCount // Nombre de (re)connexions
        ) {

}
