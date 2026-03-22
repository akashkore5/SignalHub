# AGENTS.md
## Architecture
SignalHub is a Spring Boot microservice for event-driven notifications in Agri-Nexus. It consumes Kafka events, processes notifications via providers (Email: Brevo/SES, Push: Firebase), and integrates AI agents for query routing and specialized advice.
Key components:
- **Notifications**: Kafka consumers (NotificationConsumer) process events from 'notifications' and 'notification-requests' topics.
- **AI Agents**: Graph-based system starting with RouterAgent (uses Gemini AI) routing to specialized agents (Diagnosis, Market, Advisory) for farming queries.
- **Data**: MongoDB for notifications/logs, Redis for cache/idempotency/rate limiting.
- **External**: Confluent Kafka, Upstash Redis, Atlas MongoDB, Brevo email, Firebase push.
## Workflows
- **Build**: mvn clean package (Docker multi-stage build).
- **Run**: mvn spring-boot:run or java -jar target/event-0.0.1-SNAPSHOT.jar.
- **Test**: python test_producer.py sends test events to Kafka.
- **Debug**: Use MDC logging (traceId, eventId, userId, type) for tracing.
## Patterns
- **Idempotency**: Redis keys 'idempotency:notif:%s' prevent duplicates (24h TTL).
- **Rate Limiting**: Redis 'rate:notif:%s:%s' (5/min per user/type).
- **Async Processing**: @Async with @Retryable (4 attempts, exponential backoff).
- **Error Handling**: Circuit breakers (Resilience4j), logs to MongoDB, analytics to Kafka.
- **Templates**: Thymeleaf multi-language emails in src/main/resources/templates/{en,hn,kn,mr,ta,te}/.
- **AI Integration**: Agents use GeminiClientService for text/image analysis; prompts in 
esources/prompts/.
## Key Files
- NotificationProcessingService.java: Core logic, processes requests, sends to providers.
- RouterAgent.java: Routes queries to agents based on Gemini prompt.
- pplication.properties: Config for Kafka, MongoDB, Redis, email providers.
- pom.xml: Dependencies (Spring Boot 3.5.7, Kafka, MongoDB, Redis, OpenTelemetry).
- Need to logs to MongoDB collection using logService.storeLog() for all successful and failed notifications, including details like eventId, userId, type, status, and error messages if any.
Examples:
- Send notification: Set sendPush=true, sendEmail=true in NotificationRequestEvent.
- Agent query: POST /api/agent/query with AgentContext (userQuery, crop, metadata).
## Coding Standards
- Follow Java naming conventions (camelCase for variables/methods, PascalCase for classes).
- Use Lombok for boilerplate code (getters/setters, constructors).
- Proper exception handling with custom exceptions (e.g., NotificationException).
- Use @Service, @Component, @Repository annotations for Spring beans.
- Use latest Java features (records, var, switch expressions) where appropriate.
- Write clean, modular code with single responsibility principle.
- Include Javadoc comments for public methods and classes.
- Use mappers (e.g., MapStruct) for DTO conversions.
- Ensure thread safety in async processing and shared resources (e.g., Redis).
- Use configuration properties classes for external configs (e.g., KafkaConfig, EmailConfig).
- give meaningful method names and variable names for readability.
## Knowledge Graph
- **Entities**: Notification, User, Event, Agent, Query, Log.
- **Relationships**: User sends Notification, Notification is triggered by Event, Agent processes Query, Log records Notification status.
- **Attributes**: Notification (id, type, content, status), User (id, name, contact), Event (id, type, payload), Agent (id,
- name, specialty), Query (id, userQuery, crop, metadata), Log (id, eventId, userId, type, status, errorMessage).
## Documentation
- **README.md**: Overview, setup instructions, usage examples.
- **AGENTS.md**: Architecture, workflows, patterns, key files, coding standards.
- **Skills**: Java, Spring Boot, Kafka, MongoDB, Redis, AI integration (Gemini), email/push providers, logging, error handling, async processing.
- update AGENTS.md with any new architectural changes, workflows, patterns, or coding standards as the project evolves.
- Ensure all new features or changes are reflected in the documentation for consistency and ease of onboarding new developers or agents.
- Use diagrams (e.g., sequence diagrams, component diagrams) in AGENTS.md to visually represent workflows and architecture for better understanding.
- Ensure Information.md is updated with any new integrations, features, or architectural changes to maintain a comprehensive project summary for reference.
- Regularly review and update AGENTS.md to ensure it remains accurate and useful for AI coding agents working on the codebase, especially as new features or changes are implemented.
