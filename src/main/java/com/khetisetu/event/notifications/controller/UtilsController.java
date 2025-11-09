package com.khetisetu.event.notifications.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/")
public class UtilsController {

    @GetMapping("/hero")
    public ResponseEntity<List<String>> getHeroImages() {
        List<String> heroImages = Arrays.asList("https://placeholder.com/800x400?text=Hero+Image+1",
                "https://placeholder.com/800x400?text=Hero+Image+2",
                "https://placeholder.com/800x400?text=Hero+Image+3");
        return ResponseEntity.ok(heroImages);
    }
}