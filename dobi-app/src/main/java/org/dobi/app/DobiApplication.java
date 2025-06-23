package org.dobi.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"org.dobi.app", "org.dobi.manager", "org.dobi.kafka"})
public class DobiApplication {

    public static void main(String[] args) {
        // Cela va démarrer le serveur web ET scanner les beans (comme SupervisionService)
        SpringApplication.run(DobiApplication.class, args);
    }
}
