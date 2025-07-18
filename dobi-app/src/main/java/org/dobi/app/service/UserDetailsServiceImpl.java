package org.dobi.app.service;

import org.dobi.app.repository.UserLoginDataRepository;
import org.dobi.entities.UserLoginData;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserLoginDataRepository userRepository;

    public UserDetailsServiceImpl(UserLoginDataRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserLoginData user = userRepository.findByLoginNameWithRoles(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé: " + username));

        return new User(
                user.getLoginName(),
                user.getPasswordHash(),
                user.getUserAccount().getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName())) // Le préfixe ROLE_ est une convention Spring
                        .collect(Collectors.toSet())
        );
    }
}
