package com.khetisetu.event.notifications.controller;

import com.khetisetu.event.notifications.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/")
public class UtilsController {

    private final NotificationRepository notificationRepository;

    public UtilsController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/hero")
    public ResponseEntity<List<String>> getHeroImages() {
        List<String> heroImages = Arrays.asList("https://placeholder.com/800x400?text=Hero+Image+1",
                "https://placeholder.com/800x400?text=Hero+Image+2",
                "https://placeholder.com/800x400?text=Hero+Image+3");
        return ResponseEntity.ok(heroImages);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();

        try {
            long count = notificationRepository.count();
            response.put("status", "OK");
            response.put("kafka", "Consumer Running");
            response.put("notifications", count);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            response.put("status", "ERROR");
            response.put("details", "DB Unreachable or Kafka Issue");
            return ResponseEntity.status(200).body(response);
        }
    }


}