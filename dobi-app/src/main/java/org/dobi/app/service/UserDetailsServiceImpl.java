package org.dobi.app.service;

import org.dobi.app.repository.UserLoginDataRepository;
import org.dobi.entities.UserLoginData;
import org.dobi.logging.LogLevelManager; // <-- NOUVEL IMPORT
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final String COMPONENT_NAME = "USER-DETAILS-SERVICE";
    private final UserLoginDataRepository userRepository;

    public UserDetailsServiceImpl(UserLoginDataRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LogLevelManager.logInfo(COMPONENT_NAME, "Tentative de chargement de l'utilisateur : '" + username + "'");

        UserLoginData user = userRepository.findByLoginNameWithRoles(username)
                .orElseThrow(() -> {
                    LogLevelManager.logError(COMPONENT_NAME, "Utilisateur non trouvé dans la base de données : '" + username + "'");
                    return new UsernameNotFoundException("Utilisateur non trouvé: " + username);
                });

        LogLevelManager.logInfo(COMPONENT_NAME, "Utilisateur '" + username + "' trouvé. Hash du mot de passe depuis la BDD : " + user.getPasswordHash());
        LogLevelManager.logInfo(COMPONENT_NAME, "Nombre de rôles trouvés : " + user.getUserAccount().getRoles().size());
        user.getUserAccount().getRoles().forEach(role -> LogLevelManager.logDebug(COMPONENT_NAME, "Rôle : " + role.getName()));

        return new User(
                user.getLoginName(),
                user.getPasswordHash(),
                user.getUserAccount().getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                        .collect(Collectors.toSet())
        );
    }
}
