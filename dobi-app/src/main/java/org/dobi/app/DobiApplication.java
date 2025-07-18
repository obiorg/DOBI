package org.dobi.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan; // <-- NOUVEL IMPORT
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan; // <-- NOUVEL IMPORT
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Classe principale de l'application DOBI.
 *
 * CORRECTION MAJEURE : Pour résoudre les erreurs de configuration complexes
 * ("Unable to start web server", "Cannot resolve reference to bean
 * 'jpaSharedEM_entityManagerFactory'"), nous centralisons toute la
 * configuration de scan dans cette classe principale.
 *
 * 1. @ComponentScan: Demande à Spring de scanner tous les packages du projet
 * pour trouver les beans (@Service, @Controller, etc.). 2. @EntityScan: Indique
 * explicitement à Spring/JPA où trouver les classes d'entités (@Entity). C'est
 * plus robuste que de se fier à persistence.xml. 3. @EnableJpaRepositories:
 * Active la création des beans de repository (comme UserLoginDataRepository).
 *
 * Cette configuration centralisée garantit que Spring construit son contexte
 * d'application dans le bon ordre et que tous les beans sont correctement liés
 * entre eux.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = "org.dobi") // Scanne tout le projet pour les beans
@EntityScan(basePackages = "org.dobi.entities") // Indique où sont les entités
@EnableJpaRepositories(basePackages = "org.dobi.app.repository") // Indique où sont les repositories
public class DobiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DobiApplication.class, args);
    }

}
