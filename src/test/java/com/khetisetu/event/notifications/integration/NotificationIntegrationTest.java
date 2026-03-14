package com.khetisetu.event.notifications.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.MockBeans;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.model.Notification;
import com.khetisetu.event.notifications.provider.NotificationProvider;
import com.khetisetu.event.notifications.repository.NotificationRepository;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "notification-requests" })
@ActiveProfiles("test")
@MockBeans({
        @MockBean(name = "pushNotificationProvider", classes = NotificationProvider.class),
        @MockBean(name = "emailNotificationProvider", classes = NotificationProvider.class),
        @MockBean(com.google.firebase.FirebaseApp.class)
})
class NotificationIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    private static de.flapdoodle.embed.mongo.transitions.RunningMongodProcess mongodProcess;

    @DynamicPropertySource
    static void setMongoProperties(DynamicPropertyRegistry registry) {
        mongodProcess = de.flapdoodle.embed.mongo.transitions.Mongod.instance()
                .start(de.flapdoodle.embed.mongo.distribution.Version.Main.V6_0)
                .current();
        int port = mongodProcess.getServerAddress().getPort();
        registry.add("spring.data.mongodb.port", () -> port);
        registry.add("spring.data.mongodb.host", () -> "localhost");
        registry.add("spring.data.mongodb.uri", () -> "mongodb://localhost:" + port + "/test");
    }

    @AfterAll
    static void stopMongo() {
        if (mongodProcess != null) {
            mongodProcess.stop();
        }
    }

    @Test
    void endToEndFlow_ShouldProcessEventAndStoreInMongo() {
        // Arrange
        String userId = "user_" + UUID.randomUUID();
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(userId)
                .recipient(userId)
                .type("PUSH")
                .templateName("test_template")
                .params(new HashMap<>())
                .build();

        // Act
        kafkaTemplate.send("notification-requests", event);

        // Assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findByRecipient(userId);
            assertNotNull(notifications);
            assertEquals(1, notifications.size());
            assertEquals("test_template", notifications.get(0).getTemplateName());
        });
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class KafkaTestConfig {
        @Autowired
        private org.springframework.boot.autoconfigure.kafka.KafkaProperties properties;

        @Bean
        public org.springframework.kafka.core.ProducerFactory<String, Object> producerFactory() {
            Map<String, Object> configProps = new HashMap<>(properties.buildProducerProperties(null));
            configProps.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                    org.apache.kafka.common.serialization.StringSerializer.class);
            configProps.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                    org.springframework.kafka.support.serializer.JsonSerializer.class);
            return new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(configProps);
        }

        @Bean
        public KafkaTemplate<String, Object> kafkaTemplate() {
            return new KafkaTemplate<>(producerFactory());
        }
    }
}
