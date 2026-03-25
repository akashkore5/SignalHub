package com.khetisetu.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EnableAsync
@EnableCaching
@EnableMongoRepositories(basePackages = "com.khetisetu.event.notifications.repository")
@RestController
public class NotificationEventServiceApplication {
	public static void main(String[] args) {
		// Force pure Java Snappy — fixes Alpine error 100%
		System.setProperty("org.xerial.snappy.use.systemlib", "false");
		System.setProperty("org.xerial.snappy.tempdir", "/tmp");
		SpringApplication.run(NotificationEventServiceApplication.class, args);
	}
}
