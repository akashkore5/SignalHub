package com.khetisetu.event.notifications.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// src/main/java/com/khetisetu/event/notifications/config/TracingConfig.java
@Configuration
public class TracingConfig {

        @Bean
        public OpenTelemetry openTelemetry() {
                // Use No-op if you don't need tracing, or fix by not registering global if
                // already registered.
                // For now, removing .buildAndRegisterGlobal() and using .build() to avoid
                // crash.
                return OpenTelemetrySdk.builder()
                                .setTracerProvider(
                                                SdkTracerProvider.builder()
                                                                .setResource(Resource.getDefault()
                                                                                .merge(Resource.builder()
                                                                                                .put("service.name",
                                                                                                                "notification-delivery-ms")
                                                                                                .build()))
                                                                .build())
                                .setPropagators(ContextPropagators.create(
                                                W3CTraceContextPropagator.getInstance()))
                                .build();
        }

        @Bean
        public Tracer tracer(OpenTelemetry openTelemetry) {
                return openTelemetry.getTracer("notification-delivery");
        }
}