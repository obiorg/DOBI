package org.dobi.app.config;

import org.dobi.app.service.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@ComponentScan(basePackages = {
    "org.dobi.services", // Pour AlarmEngineService
    "org.dobi.manager", // Pour MachineManagerService  
    "org.dobi.kafka", // Pour KafkaManagerService
    "org.dobi.influxdb", // Pour les services InfluxDB
    "org.dobi.core.websocket" // Pour TagWebSocketController
})
public class DobiServiceConfiguration {

    private final UserDetailsServiceImpl userDetailsService;

    public DobiServiceConfiguration(UserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * Définit le fournisseur d'authentification. C'est le bean qui manquait et
     * qui causait l'erreur de démarrage. Il utilise le service
     * UserDetailsServiceImpl pour trouver les utilisateurs et le
     * PasswordEncoder pour vérifier les mots de passe.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Expose l'AuthenticationManager en tant que bean pour qu'il puisse être
     * utilisé dans le AuthController pour le processus de login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Définit l'encodeur de mot de passe à utiliser dans l'application. BCrypt
     * est le standard recommandé.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
