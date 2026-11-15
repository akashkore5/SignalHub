package com.khetisetu.event.notifications.service;

import com.khetisetu.event.notifications.model.Tenant;
import com.khetisetu.event.notifications.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a tenantId to the identity we send under.
 *
 * <p>Cached in memory because it is read on every single notification and
 * changes approximately never; {@link #refresh()} re-reads after an edit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository repository;

    /**
     * Applied when a message carries no tenantId. Every khetisetu producer
     * predates this field, so without a default the cutover would require every
     * producer to be redeployed in lockstep with this service.
     */
    @Value("${tenant.default:khetisetu}")
    private String defaultTenantId;

    private final Map<String, Tenant> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void refresh() {
        try {
            List<Tenant> all = repository.findAll();
            cache.clear();
            all.forEach(t -> cache.put(t.getId(), t));
            log.info("Tenant registry loaded: {}", cache.keySet());
        } catch (Exception e) {
            // A Mongo hiccup at boot must not take the service down — resolve()
            // falls back to the hardcoded identity until the next refresh.
            log.error("Could not load tenant registry: {}", e.getMessage());
        }
    }

    /** Null/blank resolves to the default tenant. */
    public Optional<Tenant> find(String tenantId) {
        String id = (tenantId == null || tenantId.isBlank()) ? defaultTenantId : tenantId;
        Tenant t = cache.get(id);
        if (t == null) {
            // Could be a tenant added since boot; one lookup before giving up.
            t = repository.findById(id).orElse(null);
            if (t != null) cache.put(id, t);
        }
        return Optional.ofNullable(t);
    }

    /**
     * Resolve for sending. An unknown or disabled tenant throws rather than
     * silently sending under another product's name — a dyopact user receiving
     * mail branded KhetiSetu is worse than a failed send we can see and fix.
     */
    public Tenant resolveForSend(String tenantId) {
        String id = (tenantId == null || tenantId.isBlank()) ? defaultTenantId : tenantId;
        Tenant t = find(id).orElseThrow(
                () -> new IllegalStateException("Unknown tenant: " + id));
        if (!t.isEnabled()) {
            throw new IllegalStateException("Tenant is disabled: " + id);
        }
        return t;
    }

    public List<Tenant> findAll() {
        return repository.findAll();
    }

    public Tenant save(Tenant tenant) {
        Tenant saved = repository.save(tenant);
        cache.put(saved.getId(), saved);
        return saved;
    }

    public String defaultTenantId() {
        return defaultTenantId;
    }
}
