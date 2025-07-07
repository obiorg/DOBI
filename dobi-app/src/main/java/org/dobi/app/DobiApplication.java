package org.dobi.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean; // Import pour @Bean
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer; // Import pour WebMvcConfigurer

@SpringBootApplication
@ComponentScan(basePackages = "org.dobi")
public class DobiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DobiApplication.class, args);
    }

    /**
     * Configuration CORS pour permettre les connexions depuis le frontend.
     * IMPORTANT : Pour la production, remplacez "http://localhost",
     * "http://10.242.14.3" et "null" par les origines exactes de votre
     * application frontend.
     */
//    @Bean
//    public WebMvcConfigurer corsConfigurer() {
//        return new WebMvcConfigurer() {
//            @Override
//            public void addCorsMappings(CorsRegistry registry) {
//                registry.addMapping("/**") // Applique la configuration CORS à toutes les routes
//                        .allowedOrigins(
//                                "http://localhost", // Pour les tests locaux via un serveur web
//                                "http://10.242.14.3", // Pour l'accès direct via l'IP de votre serveur DOBI
//                                "null" // Pour les fichiers HTML ouverts directement depuis le système de fichiers (origin est 'null')
//                        )
//                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Méthodes HTTP autorisées
//                        .allowedHeaders("*") // Tous les en-têtes sont autorisés
//                        .allowCredentials(true);      // Autorise l'envoi de cookies et d'informations d'authentification
//            }
//        };
//    }
}
