package com.khetisetu.event.notifications.config;

import com.khetisetu.event.notifications.model.Tenant;
import com.khetisetu.event.notifications.repository.TenantRepository;
import com.khetisetu.event.notifications.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the known tenants on first boot.
 *
 * <p>Insert-only on purpose: an existing document is left untouched, so editing
 * a sender address in Mongo is not silently reverted on the next deploy.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class TenantSeeder {

    private final TenantRepository repository;
    private final TenantService tenantService;

    @Bean
    public ApplicationRunner seedTenants() {
        return args -> {
            seed("khetisetu", "KhetiSetu", "support@khetisetu.com", "https://khetisetu.com");
            seed("dyopact", "Dyopact", "support@dyopact.com", "https://dyopact.com");
            tenantService.refresh();
        };
    }

    private void seed(String id, String name, String senderEmail, String siteUrl) {
        if (repository.existsById(id)) {
            log.debug("Tenant {} already present — leaving as is", id);
            return;
        }
        Tenant t = new Tenant();
        t.setId(id);
        t.setName(name);
        t.setSenderEmail(senderEmail);
        t.setSenderName(name);
        t.setSiteUrl(siteUrl);
        t.setEnabled(true);
        repository.save(t);
        log.info("Seeded tenant {} <{}>", id, senderEmail);
    }
}
