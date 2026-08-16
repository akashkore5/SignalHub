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
 * Ensures the 'application-logs' topic exists in Aiven Cloud
 * before the LogConsumer attempts to subscribe to it.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.sasl-jaas-config:}")
    private String saslJaasConfig;

    @Value("${kafka.sasl-mechanism:SCRAM-SHA-256}")
    private String saslMechanism;

    @Value("${kafka.topic.replicas:2}")
    private int topicReplicas;

    @Value("${kafka.topic.partitions:2}")
    private int topicPartitions;

    private final KafkaSslConfig kafkaSslConfig;

    public KafkaTopicConfig(KafkaSslConfig kafkaSslConfig) {
        this.kafkaSslConfig = kafkaSslConfig;
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        if (saslJaasConfig != null && !saslJaasConfig.isEmpty()) {
            configs.put("security.protocol", "SASL_SSL");
            configs.put("sasl.mechanism", saslMechanism);
            configs.put("sasl.jaas.config", saslJaasConfig);
            // Aiven uses its own project CA - must be trusted (inline PEM)
            String sslCa = kafkaSslConfig.resolveCa();
            if (!sslCa.isEmpty()) {
                configs.put("ssl.truststore.type", "PEM");
                configs.put("ssl.truststore.certificates", sslCa);
            }
        }

        KafkaAdmin admin = new KafkaAdmin(configs);
        admin.setFatalIfBrokerNotAvailable(false);
        admin.setAutoCreate(true);
        return admin;
    }

    @Bean
    public NewTopic applicationLogsTopic() {
        return TopicBuilder.name("application-logs")
                .partitions(topicPartitions)
                .replicas(topicReplicas)
                .build();
    }

    // NOTE: agnexus-queries/agnexus-responses topics are intentionally NOT created.
    // The Aiven plan caps user topics at 5, and the async agnexus Kafka path is
    // unused (agent chat runs synchronously via AgentController REST). Re-add these
    // and set agnexus.kafka.enabled=true when the plan is upgraded.

    @Bean
    public NewTopic notificationsTopic() {
        return TopicBuilder.name("notifications")
                .partitions(topicPartitions)
                .replicas(topicReplicas)
                .build();
    }

    @Bean
    public NewTopic notificationRequestsTopic() {
        return TopicBuilder.name("notification-requests")
                .partitions(topicPartitions)
                .replicas(topicReplicas)
                .build();
    }

    @Bean
    public NewTopic userActivityAnalyticsTopic() {
        return TopicBuilder.name("user-activity-analytics")
                .partitions(topicPartitions)
                .replicas(topicReplicas)
                .build();
    }
}
