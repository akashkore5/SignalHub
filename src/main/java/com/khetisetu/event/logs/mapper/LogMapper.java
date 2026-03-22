package com.khetisetu.event.logs.mapper;


import com.khetisetu.event.notifications.model.logs.Actor;
import com.khetisetu.event.notifications.model.logs.Entity;
import com.khetisetu.event.notifications.model.logs.Log;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper interface for converting Actor, Entity, and Log details into a Log object.
 * Utilizes MapStruct for automatic mapping generation.
 */
@Mapper(componentModel = "spring")
public interface LogMapper {

    /**
     * Maps the provided Actor, action, Entity, details, and level to a Log object.
     *
     * @param actor   the actor performing the action
     * @param action  the action being performed
     * @param entity  the entity on which the action is performed
     * @param details additional details about the action
     * @param level   the log level (e.g., INFO, ERROR)
     * @return a Log object containing the mapped information
     */
    @Mapping(target = "id", expression = "java(\"LOG\" + java.lang.System.currentTimeMillis())")
    @Mapping(target = "timestamp", expression = "java(java.time.Instant.now())")
    Log mapToLog(Actor actor, String action, Entity entity, String details, String level);
}
