package com.khetisetu.event.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka admin configuration to auto-create required topics.
 * Ensures the 'application-logs' topic exists in Confluent Cloud
 * before the LogConsumer attempts to subscribe to it.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.sasl-jaas-config:}")
    private String saslJaasConfig;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        if (saslJaasConfig != null && !saslJaasConfig.isEmpty()) {
            configs.put("security.protocol", "SASL_SSL");
            configs.put("sasl.mechanism", "PLAIN");
            configs.put("sasl.jaas.config", saslJaasConfig);
        }

        KafkaAdmin admin = new KafkaAdmin(configs);
        admin.setFatalIfBrokerNotAvailable(false);
        admin.setAutoCreate(true);
        return admin;
    }

    @Bean
    public NewTopic applicationLogsTopic() {
        return TopicBuilder.name("application-logs")
                .partitions(6)
                .replicas(3)
                .build();
    }
}
