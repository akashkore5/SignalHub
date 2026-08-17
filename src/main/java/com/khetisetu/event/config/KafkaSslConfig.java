package com.khetisetu.event.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the Aiven project CA certificate and injects it (as an inline PEM
 * truststore) into every Kafka client.
 *
 * <p>Aiven uses its own project CA, so the CA must be trusted or the TLS
 * handshake fails. The cert is public (not a secret), so it ships as a
 * classpath resource (aiven-ca.pem) and works both locally and inside the
 * deployed jar — where {@code ssl.truststore.location} file paths are not
 * readable but classpath resources are. The {@code KAFKA_SSL_CA} env var
 * (property {@code kafka.ssl-ca}) overrides the bundled cert for rotation.
 *
 * <p>The customizers apply the CA to Spring Boot's auto-configured
 * consumer/producer factories (used by the plain @KafkaListener beans).
 * Manually-built clients (e.g. the KafkaAdmin in KafkaTopicConfig) call
 * {@link #resolveCa()} directly.
 */
@Configuration
public class KafkaSslConfig {

    @Value("${kafka.ssl-ca:}")
    private String sslCaProperty;

    @Value("${kafka.username:}")
    private String username;

    @Value("${kafka.password:}")
    private String password;

    @Value("${kafka.sasl-mechanism:SCRAM-SHA-256}")
    private String saslMechanism;

    /**
     * Full SASL_SSL security config for an Aiven client, built from
     * kafka.username/kafka.password (env KAFKA_USERNAME/KAFKA_PASSWORD) so it works
     * even when application.properties is absent (e.g. on Render, where config is
     * env-only). Returns an empty map only when no credentials are present (local
     * no-auth broker) — this avoids silently falling back to PLAINTEXT against a TLS
     * port, which manifests as an OutOfMemoryError. Prefer this over a hand-crafted
     * KAFKA_SASL_JAAS_CONFIG env string.
     */
    public Map<String, Object> securityProps() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Map.of();
        }
        Map<String, Object> props = new HashMap<>();
        props.put("security.protocol", "SASL_SSL");
        props.put("sasl.mechanism", saslMechanism);
        props.put("sasl.jaas.config",
                "org.apache.kafka.common.security.scram.ScramLoginModule required "
                        + "username=\"" + username + "\" password=\"" + password + "\";");
        String ca = resolveCa();
        if (!ca.isEmpty()) {
            props.put("ssl.truststore.type", "PEM");
            props.put("ssl.truststore.certificates", ca);
        }
        return props;
    }

    /** Returns the CA PEM: the env/property override if set, else the bundled resource, else "". */
    public String resolveCa() {
        if (sslCaProperty != null && !sslCaProperty.isBlank()) {
            return sslCaProperty;
        }
        ClassPathResource resource = new ClassPathResource("aiven-ca.pem");
        if (!resource.exists()) {
            return "";
        }
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Aiven CA cert (aiven-ca.pem)", e);
        }
    }

    @Bean
    public DefaultKafkaConsumerFactoryCustomizer aivenConsumerCaCustomizer() {
        Map<String, Object> configs = securityProps();
        return factory -> {
            if (!configs.isEmpty()) {
                factory.updateConfigs(configs);
            }
        };
    }

    @Bean
    public DefaultKafkaProducerFactoryCustomizer aivenProducerCaCustomizer() {
        Map<String, Object> configs = securityProps();
        return factory -> {
            if (!configs.isEmpty()) {
                factory.updateConfigs(configs);
            }
        };
    }
}
