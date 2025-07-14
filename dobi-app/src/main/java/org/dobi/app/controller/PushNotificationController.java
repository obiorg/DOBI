package org.dobi.app.controller;

import org.dobi.app.service.PushNotificationService;
import org.dobi.dto.PushSubscriptionDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class PushNotificationController {

    private final PushNotificationService pushService;

    public PushNotificationController(PushNotificationService pushService) {
        this.pushService = pushService;
    }

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> subscribe(@RequestBody PushSubscriptionDto subscription) {
        pushService.subscribe(subscription);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
