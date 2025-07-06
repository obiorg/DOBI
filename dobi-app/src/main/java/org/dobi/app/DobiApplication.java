package org.dobi.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan; // Assurez-vous que cet import est là

@SpringBootApplication
// Si votre application principale est dans org.dobi.app, et que vos beans sont dans
// org.dobi.manager, org.dobi.app.service, org.dobi.app.controller, org.dobi.influxdb,
// et org.dobi.core.websocket, le @ComponentScan par défaut de Spring Boot
// (qui scanne le package de la classe principale et ses sous-packages)
// pourrait ne pas couvrir tous les packages si org.dobi.core est un package "frère" de org.dobi.app.
// Il est plus sûr d'ajouter un ComponentScan explicite couvrant la racine de votre projet.
@ComponentScan(basePackages = "org.dobi") // AJOUTÉ/MODIFIÉ : Scanne tous les packages sous org.dobi
public class DobiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DobiApplication.class, args);
    }
}