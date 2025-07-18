package org.dobi.app.repository;

import org.dobi.entities.UserLoginData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserLoginDataRepository extends JpaRepository<UserLoginData, Long> {

    // Spring Data JPA va automatiquement générer la requête pour cette méthode.
    // On utilise une requête personnalisée pour s'assurer de charger les rôles en même temps.
    @Query("SELECT u FROM UserLoginData u JOIN FETCH u.userAccount ua JOIN FETCH ua.roles WHERE u.loginName = :username")
    Optional<UserLoginData> findByLoginNameWithRoles(String username);
}
