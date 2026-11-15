package com.khetisetu.event.notifications.repository;

import com.khetisetu.event.notifications.model.Tenant;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TenantRepository extends MongoRepository<Tenant, String> {
}
