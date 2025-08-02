package org.dobi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principale de l'application Spring Boot.
 *
 * L'annotation @SpringBootApplication est configurée pour scanner tous les
 * sous-packages à partir de "org.dobi". C'est la méthode standard et la plus
 * robuste pour s'assurer que Spring Boot découvre automatiquement tous les
 * composants, entités, et repositories à travers les différents modules du
 * projet (dobi-app, dobi-core, dobi-services, etc.).
 *
 * Cette seule annotation remplace les @ComponentScan, @EntityScan, et
 * @EnableJpaRepositories explicites pour simplifier la configuration et éviter
 * les conflits.
 */
@SpringBootApplication(scanBasePackages = "org.dobi")
public class DobiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DobiApplication.class, args);
    }
}
