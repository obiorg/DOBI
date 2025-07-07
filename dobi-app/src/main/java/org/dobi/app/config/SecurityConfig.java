package org.dobi.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Applique la configuration CORS existante
                .cors(withDefaults())
                // Désactive la protection CSRF, cause de l'erreur 403
                .csrf(csrf -> csrf.disable())
                // Autorise toutes les requêtes HTTP sans authentification.
                // C'est acceptable pour le développement. Nous pourrons ajouter
                // une authentification plus tard (ex: JWT).
                .authorizeHttpRequests(authz -> authz
                .requestMatchers("/**").permitAll()
                .anyRequest().authenticated()
                );

        return http.build();
    }
}
