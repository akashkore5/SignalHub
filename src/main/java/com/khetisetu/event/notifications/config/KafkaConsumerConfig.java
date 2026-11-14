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
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import org.apache.kafka.common.serialization.Deserializer;

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

    /**
     * Wraps a value deserializer so that an undeserializable record becomes a
     * recoverable, record-level error instead of a fatal one.
     *
     * Without this, deserialization happens inside the consumer's poll loop —
     * BEFORE any CommonErrorHandler can see the record — so a single malformed
     * message throws out of poll(), the container retries the same offset, and
     * the partition is blocked permanently. No error handler can rescue it,
     * because the error handler is never reached.
     *
     * This is not hypothetical: a plain-text smoke-test message at offset 0 of
     * notifications/p2 blocked that partition indefinitely, silently stranding
     * every real notification queued behind it.
     */
    private static <T> Deserializer<T> safeJson(Class<T> type) {
        return new ErrorHandlingDeserializer<>(new JsonDeserializer<>(type, false));
    }

    /**
     * Logs and skips a record that could not be processed, rather than retrying
     * it forever. Pairs with {@link #safeJson}: that surfaces the bad record,
     * this one lets the container move past it and commit.
     */
    private static DefaultErrorHandler skipAfterRetries(String what) {
        return new DefaultErrorHandler(
                (record, ex) -> LoggerFactory.getLogger(KafkaConsumerConfig.class)
                        .error("Dropping unprocessable {} at {}-{}@{} after retries: {}",
                                what, record.topic(), record.partition(), record.offset(), record.value(), ex),
                new FixedBackOff(1000L, 3));
    }

    // DIRECT: NotificationEvent
    @Bean
    public ConsumerFactory<String, NotificationEvent> directConsumerFactory() {
        Map<String, Object> props = baseProps("notification-event-group");
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                safeJson(NotificationEvent.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> directFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>();
        factory.setConsumerFactory(directConsumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(skipAfterRetries("notification event"));
        return factory;
    }

    // RULE-BASED: NotificationRequestEvent
    @Bean
    public ConsumerFactory<String, NotificationRequestEvent> ruleConsumerFactory() {
        Map<String, Object> props = baseProps("delivery-group");
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                safeJson(NotificationRequestEvent.class)
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
                safeJson(Object.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> analyticsFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(analyticsConsumerFactory());
        factory.setConcurrency(2);
        factory.setCommonErrorHandler(skipAfterRetries("analytics event"));
        return factory;
    }

    // LOG EVENTS: LogEvent from main backend
    @Bean
    public ConsumerFactory<String, LogEvent> logConsumerFactory() {
        Map<String, Object> props = baseProps("log-consumer-group");
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                safeJson(LogEvent.class)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LogEvent> logFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, LogEvent>();
        factory.setConsumerFactory(logConsumerFactory());
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(skipAfterRetries("log event"));
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

        // Full SASL_SSL + SCRAM + Aiven CA, built from KAFKA_USERNAME/KAFKA_PASSWORD.
        // Works even without application.properties (env-only deploys like Render).
        props.putAll(kafkaSslConfig.securityProps());

        props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, true);
        return props;
    }
}