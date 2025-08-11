package org.dobi.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Configure les autorisations CORS pour l'ensemble de l'application. C'est
     * la méthode recommandée pour gérer les problèmes de Cross-Origin.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // Applique les règles CORS à toutes les routes sous /api/
                .allowedOrigins("https://localhost:3000", "http://localhost:3000") // Autorise les requêtes depuis ces origines
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Autorise ces méthodes HTTP
                .allowedHeaders("*") // Autorise tous les en-têtes
                .allowCredentials(true); // Autorise l'envoi de cookies et d'en-têtes d'authentification
    }
}
