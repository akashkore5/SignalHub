package com.khetisetu.event.notifications.model.logs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an actor in the system, such as a user or an admin.
 * This class is used to log actions performed by different actors.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Actor {
    private String id;
    private String name;
}
