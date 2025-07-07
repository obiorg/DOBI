package org.dobi.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration; // <-- IMPORT NÉCESSAIRE

/**
 * Classe principale de l'application DOBI.
 *
 * L'attribut 'exclude' a été ajouté à l'annotation @SpringBootApplication pour
 * désactiver la configuration automatique de la source de données par Spring.
 * Cela est nécessaire car la configuration de la base de données est gérée
 * manuellement via persistence.xml et EntityManagerFactory dans
 * MachineManagerService. Cette modification résout l'erreur "Failed to
 * configure a DataSource".
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class}) // <-- MODIFICATION ICI
public class DobiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DobiApplication.class, args);
    }

}
