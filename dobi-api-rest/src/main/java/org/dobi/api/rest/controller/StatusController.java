package org.dobi.api.rest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/status")
public class StatusController {

    @GetMapping
    public Map<String, String> getStatus() {
        // Cet endpoint simple confirme que l'API est en ligne.
        return Map.of("status", "OK", "application", "DOBI API REST");
    }
}
