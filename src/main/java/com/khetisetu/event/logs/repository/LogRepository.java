package com.khetisetu.event.logs.repository;

import com.khetisetu.event.notifications.model.logs.Log;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Repository interface for managing Log entities in MongoDB.
 * Provides methods to query logs based on action, details, and log level.
 */
public interface LogRepository extends MongoRepository<Log, String> {

    /**
     * Finds logs by action and log level.
     *
     * @param actionRegex the action to filter logs by
     * @param levels the log levels to filter by
     * @param pageable pagination information
     * @return a page of logs matching the criteria
     */
    @Query("{ 'action': { $regex: ?0, $options: 'i' }, 'level': { $in: ?1 } }")
    Page<Log> findByActionRegexAndLevelIn(String actionRegex, String[] levels, Pageable pageable);

    /**
     * Finds logs by details and log level.
     *
     * @param detailsRegex the details to filter logs by
     * @param levels the log levels to filter by
     * @param pageable pagination information
     * @return a page of logs matching the criteria
     */
    @Query("{ 'details': { $regex: ?0, $options: 'i' }, 'action': { $regex: ?1, $options: 'i' }, 'level': { $in: ?2 } }")
    Page<Log> findByDetailsRegexAndActionRegexAndLevelIn(String detailsRegex, String actionRegex, String[] levels, Pageable pageable);
}