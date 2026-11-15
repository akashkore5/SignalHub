package com.khetisetu.event.notifications.controller;

import com.khetisetu.event.notifications.model.Tenant;
import com.khetisetu.event.notifications.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Manage the products this service sends on behalf of.
 *
 * <p>Under /api/v1, so ApiKeyAuthFilter already requires X-API-Key.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    public Map<String, Object> list() {
        List<Tenant> tenants = tenantService.findAll();
        return Map.of(
                "default", tenantService.defaultTenantId(),
                "count", tenants.size(),
                "tenants", tenants);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tenant> get(@PathVariable String id) {
        return tenantService.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Create or update. The id in the path wins, so a mismatched body cannot write to the wrong tenant. */
    @PutMapping("/{id}")
    public ResponseEntity<?> upsert(@PathVariable String id, @RequestBody Tenant body) {
        if (body.getSenderEmail() == null || body.getSenderEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "senderEmail is required"));
        }
        body.setId(id);
        if (body.getSenderName() == null || body.getSenderName().isBlank()) {
            body.setSenderName(body.getName() != null ? body.getName() : id);
        }
        return ResponseEntity.ok(tenantService.save(body));
    }

    /** Re-read the registry from Mongo after an out-of-band edit. */
    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        tenantService.refresh();
        return Map.of("status", "refreshed", "count", tenantService.findAll().size());
    }
}
