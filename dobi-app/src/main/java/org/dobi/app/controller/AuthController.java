package org.dobi.app.controller;

import org.dobi.app.repository.UserAccountRepository;
import org.dobi.app.security.JwtService;
import org.dobi.dto.LoginRequest;
import org.dobi.dto.LoginResponse;
import org.dobi.dto.UserDto;
import org.dobi.entities.UserAccount;
import org.dobi.entities.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails; // Import nécessaire
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService, UserAccountRepository userAccountRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        logger.info("Tentative d'authentification pour l'utilisateur : {}", loginRequest.loginName());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.loginName(), loginRequest.password()));
        logger.info("Authentification réussie pour : {}", loginRequest.loginName());

        // CORRECTION 1: Le service JWT attend un objet UserDetails, pas Authentication.
        // On l'obtient via authentication.getPrincipal().
        String jwt = jwtService.generateToken((UserDetails) authentication.getPrincipal());

        return ResponseEntity.ok(new LoginResponse(jwt));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String loginName = authentication.getName();
        // Maintenant, la méthode findByLoginName existe dans le repository.
        UserAccount userAccount = userAccountRepository.findByLoginName(loginName)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé pendant la validation du token : " + loginName));

        // CORRECTION 2: La méthode pour obtenir le nom du rôle est getName(), pas getRoleName().
        Set<String> roles = userAccount.getRoles().stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());

        // CORRECTION 3: Les getters (getLoginName, getEmail, etc.) existent maintenant sur UserAccount grâce à Lombok.
        UserDto userDto = new UserDto(
                userAccount.getId(),
                userAccount.getLoginName(),
                userAccount.getFirstName(),
                userAccount.getLastName(),
                userAccount.getEmail(),
                roles
        );

        return ResponseEntity.ok(userDto);
    }
}
