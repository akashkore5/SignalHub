// src/main/java/com/khetisetu/event/notifications/config/KafkaConsumerConfig.java
package com.khetisetu.event.notifications.config;

import com.khetisetu.event.logs.dto.LogEvent;
import com.khetisetu.event.notifications.consumer.DlqHandler;
import com.khetisetu.event.notifications.dto.NotificationEvent;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

// src/main/java/com/khetisetu/event/notifications/config/KafkaConsumerConfig.java
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${kafka.sasl-jaas-config:}")
    private String saslJaasConfig;

    @Value("${kafka.sasl-mechanism:SCRAM-SHA-256}")
    private String saslMechanism;

    private final com.khetisetu.event.config.KafkaSslConfig kafkaSslConfig;

    public KafkaConsumerConfig(com.khetisetu.event.config.KafkaSslConfig kafkaSslConfig) {
        this.kafkaSslConfig = kafkaSslConfig;
    }

    // DIRECT: NotificationEvent
    @Bean
    public ConsumerFactory<String, NotificationEvent> directConsumerFactory() {
        Map<String, Object> props = baseProps("notification-event-group");
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(NotificationEvent.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> directFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>();
        factory.setConsumerFactory(directConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    // RULE-BASED: NotificationRequestEvent
    @Bean
    public ConsumerFactory<String, NotificationRequestEvent> ruleConsumerFactory() {
        Map<String, Object> props = baseProps("delivery-group");
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(NotificationRequestEvent.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationRequestEvent> ruleFactory(
            DlqHandler dlqHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, NotificationRequestEvent>();
        factory.setConsumerFactory(ruleConsumerFactory());
        factory.setConcurrency(6);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(dlqHandler);
        return factory;
    }

    // ANALYTICS: Any Object
    @Bean
    public ConsumerFactory<String, Object> analyticsConsumerFactory() {
        Map<String, Object> props = baseProps("analytics-group");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(Object.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> analyticsFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(analyticsConsumerFactory());
        factory.setConcurrency(2);
        return factory;
    }

    // LOG EVENTS: LogEvent from main backend
    @Bean
    public ConsumerFactory<String, LogEvent> logConsumerFactory() {
        Map<String, Object> props = baseProps("log-consumer-group");
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(LogEvent.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LogEvent> logFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, LogEvent>();
        factory.setConsumerFactory(logConsumerFactory());
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // Retry transient persistence failures 3 times with 1s backoff, then log and
        // skip the record so a poison message can't block the partition forever.
        var errorHandler = new DefaultErrorHandler(
                (record, ex) -> LoggerFactory.getLogger(KafkaConsumerConfig.class)
                        .error("Dropping unprocessable log event after retries: {}", record.value(), ex),
                new FixedBackOff(1000L, 3));
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // AGNEXUS: raw String messages from agnexus-queries
    @Bean
    public ConsumerFactory<String, String> agnexusConsumerFactory() {
        Map<String, Object> props = baseProps("agnexus-group");
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new StringDeserializer()
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> agnexusFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(agnexusConsumerFactory());
        return factory;
    }

    private Map<String, Object> baseProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        if (!saslJaasConfig.isEmpty()) {
            props.put("security.protocol", "SASL_SSL");
            props.put("sasl.mechanism", saslMechanism);
            props.put("sasl.jaas.config", saslJaasConfig);
            // Aiven uses its own project CA - must be trusted (inline PEM)
            String sslCa = kafkaSslConfig.resolveCa();
            if (!sslCa.isEmpty()) {
                props.put("ssl.truststore.type", "PEM");
                props.put("ssl.truststore.certificates", sslCa);
            }
        }

        props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, true);
        return props;
    }
}