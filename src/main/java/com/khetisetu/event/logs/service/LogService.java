package com.khetisetu.event.logs.service;

import com.khetisetu.event.logs.mapper.LogMapper;
import com.khetisetu.event.logs.repository.LogRepository;
import com.khetisetu.event.notifications.model.logs.Actor;
import com.khetisetu.event.notifications.model.logs.Entity;
import com.khetisetu.event.notifications.model.logs.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;


@Service
public class LogService {

    private static final Logger LOGGER = Logger.getLogger(LogService.class.getName());

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private LogMapper logMapper;

    @Autowired
    @Qualifier("logsMongoTemplate")
    private MongoTemplate mongoTemplate;

    /**
     * Stores a log entry.
     *
     * @param actor      Actor performing the action
     * @param action     Action type
     * @param entity     Affected entity
     * @param logDetails Log details
     * @param level      Log level
     */
    public void storeLog(Actor actor, String action, Entity entity, String logDetails, String level) {
        Log log = logMapper.mapToLog(actor, action, entity, logDetails, level);
        Log saved = logRepository.save(log);
        LOGGER.info("Log stored: " + saved);
    }
}