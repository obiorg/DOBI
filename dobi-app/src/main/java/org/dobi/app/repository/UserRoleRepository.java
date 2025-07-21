package org.dobi.app.repository;

import org.dobi.entities.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Set;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    // Trouve un ensemble de rôles par leurs noms
    Set<UserRole> findByNameIn(Set<String> names);
}
