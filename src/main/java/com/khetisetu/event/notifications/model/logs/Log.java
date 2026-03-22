package com.khetisetu.event.notifications.model.logs;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Represents a log entry in the system.
 * This class is used to store logs related to various actions performed by actors on entities.
 */
@Document(collection = "logs")
@Data
public class Log {
    private String id;
    private Instant timestamp;
    private String level; // e.g., "INFO", "WARN", "ERROR"
    private Actor actor;
    private String action; // e.g., "USER_LOGIN_SUCCESS", "ECOM_PRODUCT_CREATE"
    private Entity entity;
    private String details;

    public Log(String id, Instant timestamp, String level, Actor actor, String action, Entity entity, String details) {
        this.id = id;
        this.timestamp = timestamp;
        this.level = level;
        this.actor = actor;
        this.action = action;
        this.entity = entity;
        this.details = details;
    }
}

