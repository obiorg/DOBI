package org.dobi.dto;

import java.util.Set;

// DTO pour afficher les informations d'un utilisateur
public record UserDto(
        Long id,
        String loginName,
        String firstName,
        String lastName,
        String email,
        Set<String> roles
        ) {

}
