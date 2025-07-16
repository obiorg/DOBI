package org.dobi.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Applique la configuration CORS définie ci-dessous
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Désactive la protection CSRF, cause de l'erreur 403
                .csrf(csrf -> csrf.disable())
                // Autorise toutes les requêtes HTTP sans authentification
                .authorizeHttpRequests(authz -> authz
                .requestMatchers("/**").permitAll() // Autorise tout
                .anyRequest().authenticated()
                );

        return http.build();
    }

    /**
     * Bean de configuration centralisé pour le CORS.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // CORRECTION : Ajout des origines HTTPS
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "https://localhost:3000", // Pour le développement local sécurisé
                "http://10.242.14.3:3000",
                "https://10.242.14.3:3000",
                "https://7ebee8cd7e67.ngrok-free.app"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
