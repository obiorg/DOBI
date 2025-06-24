package org.dobi.dto;

/**
 * Un DTO (Data Transfer Object) pour transporter le statut d'une machine vers
 * l'API. Un 'record' est une manière moderne et concise de créer une classe de
 * données.
 */
public record MachineStatusDto(
        long id,
        String name,
        String status,
        long tagsReadCount
        ) {

}
