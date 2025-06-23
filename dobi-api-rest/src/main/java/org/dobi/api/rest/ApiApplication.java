package org.dobi.api.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiApplication {
    public static void main(String[] args) {
        // Démarre le serveur web embarqué
        SpringApplication.run(ApiApplication.class, args);
    }
}
