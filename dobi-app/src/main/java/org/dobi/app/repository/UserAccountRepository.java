package org.dobi.app.repository;

import java.util.Optional;
import org.dobi.entities.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Integer> {

    /**
     * Ajout de la méthode pour trouver un utilisateur par son nom de login.
     * Spring Data JPA crée automatiquement la requête à partir du nom de la
     * méthode.
     *
     * @param loginName Le nom de login à rechercher.
     * @return Un Optional contenant l'UserAccount s'il est trouvé.
     */
    Optional<UserAccount> findByLoginName(String loginName);
}
