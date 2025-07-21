package org.dobi.dto;

import java.util.Set;

// DTO pour recevoir les données de création d'un nouvel utilisateur
public record CreateUserRequest(
        String loginName,
        String password,
        String firstName,
        String lastName,
        String email,
        Set<String> roles
        ) {

}
