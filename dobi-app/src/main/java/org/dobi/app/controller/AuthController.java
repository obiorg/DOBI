package org.dobi.app.controller;

import org.dobi.app.security.JwtService;
import org.dobi.dto.LoginRequest;
import org.dobi.dto.LoginResponse;
import org.dobi.logging.LogLevelManager; // <-- NOUVEL IMPORT
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String COMPONENT_NAME = "AUTH-CONTROLLER";
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, UserDetailsService userDetailsService, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LogLevelManager.logInfo(COMPONENT_NAME, "Tentative d'authentification pour l'utilisateur: " + request.username());
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
            LogLevelManager.logInfo(COMPONENT_NAME, "Authentification réussie pour : " + request.username());

            final UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
            final String jwt = jwtService.generateToken(userDetails);
            
            LogLevelManager.logInfo(COMPONENT_NAME, "Token JWT généré pour : " + request.username());
            return ResponseEntity.ok(new LoginResponse(jwt));

        } catch (BadCredentialsException e) {
            LogLevelManager.logError(COMPONENT_NAME, "Échec de l'authentification : Identifiants incorrects pour " + request.username());
            // On retourne une erreur 401 (Unauthorized) qui est plus sémantique qu'une 403 pour un login échoué.
            return ResponseEntity.status(401).body("Identifiants incorrects");
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur inattendue lors de la connexion pour " + request.username() + ": " + e.getMessage());
            return ResponseEntity.status(500).body("Erreur interne du serveur");
        }
    }
}