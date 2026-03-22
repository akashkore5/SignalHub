package com.khetisetu.event.notifications.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(
    basePackages = "com.khetisetu.app.logs.repository",
    mongoTemplateRef = "logsMongoTemplate"
)
public class LogsMongoConfig {

    @Value("${spring.data.mongodb.logs.uri}")
    private String logsUri;

    @Value("${spring.data.mongodb.logs.database}")
    private String logsDatabase;

    @Bean(name = "logsMongoClient")
    public MongoClient logsMongoClient() {
        return MongoClients.create(logsUri);
    }

    @Bean(name = "logsMongoTemplate")
    public MongoTemplate logsMongoTemplate() {
        return new MongoTemplate(logsMongoClient(), logsDatabase);
    }
}