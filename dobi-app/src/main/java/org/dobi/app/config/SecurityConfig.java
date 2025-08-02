package org.dobi.app.config;

import org.dobi.app.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, AuthenticationProvider authenticationProvider) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Désactiver CSRF pour les API stateless
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Appliquer la configuration CORS
                .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/ws/**", "/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll() // Autoriser les endpoints publics
                .anyRequest().authenticated() // Sécuriser tous les autres endpoints
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Pas de session côté serveur
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class); // Ajouter le filtre JWT

        return http.build();
    }

    /**
     * Bean de configuration CORS. C'est ici que nous autorisons le frontend à
     * communiquer avec le backend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Autorise les requêtes provenant de votre frontend Next.js
        configuration.setAllowedOrigins(Arrays.asList("https://localhost:3000", "http://localhost:3000"));
        // Autorise les méthodes HTTP communes
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Autorise tous les en-têtes
        configuration.setAllowedHeaders(List.of("*"));
        // Autorise l'envoi de cookies et d'en-têtes d'authentification
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Applique cette configuration à toutes les routes de l'application
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
