# SignalHub Project Summary
## Project Overview
SignalHub is a Spring Boot-based microservice designed for event-driven notifications in the Agri-Nexus platform. It integrates Kafka for event consumption, MongoDB and Redis for data storage and caching, and AI agents powered by Google's Gemini for intelligent query routing and specialized farming advice. The service handles notifications via email (Brevo/SES) and push notifications (Firebase), with features like idempotency, rate limiting, async processing, and multi-language support.
- **Technology Stack**: Java 21, Spring Boot 3.5.7, Kafka, MongoDB, Redis, Thymeleaf, OpenTelemetry, Resilience4j, Firebase, AWS SES, Brevo API, Gemini AI.
- **Key Features**: Event-driven architecture, AI agent routing, multi-provider notifications, logging and analytics, circuit breakers, retries, MDC tracing.
- **External Integrations**: Confluent Kafka, Atlas MongoDB, Upstash Redis, Brevo Email, Firebase Push, AWS SES.
## Project Structure
### Root Directory
- pom.xml: Maven configuration with dependencies for Spring Boot, Kafka, MongoDB, Redis, etc.
- Dockerfile: Multi-stage build for containerization.
- un_event.sh: Script to run the application with Maven.
- 	est_producer.py: Python script to produce test events to Kafka.
- AGENTS.md: Guide for AI coding agents on the codebase.
- INFORMATION: This summary file.
### src/main/java/com/khetisetu/event/
Main package for the application.
#### Main Application
- NotificationEventServiceApplication.java: Spring Boot main class with @EnableAsync and @EnableCaching. Excludes DataSource for MongoDB-only setup.
#### Config
- FirebaseConfig.java: Configuration for Firebase Admin SDK using service account credentials.
#### Constants
- EntityConstants.java: Defines constants like USER, NOTIFICATION_EVENT, etc.
- ErrorMessages.java: Error message constants.
- LogLevel.java: Log level enums (INFO, ERROR, etc.).
#### Logs Package
Handles logging to MongoDB.
- mapper/LogMapper.java: Maps log data to Log entity.
- epository/LogRepository.java: MongoDB repository for Log entities.
- service/LogService.java: Service to store logs with actor, action, entity, details, and level.
#### Notifications Package
Core notification processing.
##### Config
- AsyncConfig.java: Configures async thread pool.
- KafkaConsumerConfig.java: Kafka consumer configuration with custom deserializers.
- LogsMongoConfig.java: MongoDB config for logs database.
- RedisConfig.java: Redis configuration with connection factory.
- TracingConfig.java: OpenTelemetry tracing setup.
##### Constants
- Constants.java: Notification-related constants.
##### Consumer
- AnalyticsConsumer.java: Consumes analytics events from Kafka.
- DlqHandler.java: Handles dead letter queue for failed messages.
- NotificationConsumer.java: Main consumer for 'notifications' and 'notification-requests' topics, processes events asynchronously with tracing.
##### Controller
- UtilsController.java: Utility endpoints, e.g., for testing.
##### DTO
- NotificationAnalyticsEvent.java: DTO for analytics events.
- NotificationEvent.java: DTO for direct notification events.
- NotificationRequestEvent.java: DTO for rule-based notification requests with flags for push/email.
- UserActivityEvent.java: DTO for user activity events.
##### Model
- BaseModel.java: Base class with common fields like id, createdAt, updatedAt.
- EmailSenderConfig.java: Config for email sender details.
- Notification.java: Entity for notifications stored in MongoDB.
- NotificationTemplate.java: Model for email templates.
- logs/Actor.java: Model for log actor (id, type).
- logs/Entity.java: Model for log entity (id, type).
- logs/Log.java: Log entity with actor, action, entity, details, level, timestamp.
##### Provider
- AwsSesEmailSender.java: Email sender using AWS SES.
- BrevoEmailProvider.java: Email provider using Brevo API with circuit breaker.
- EmailProvider.java: Interface for email providers.
- EmailSender.java: Interface for email sending.
- NotificationProvider.java: Interface for notification providers (email, push).
- PushNotificationProvider.java: Push notification provider using Firebase.
##### Repository
- NotificationRepository.java: MongoDB repository for Notification entities.
##### Service
- GlobalRateLimiter.java: Service for rate limiting using Redis.
- NotificationProcessingService.java: Core service for processing notifications, handles idempotency, rate limiting, async sending to providers, logging failures.
- UserTokenService.java: Service for managing user tokens, possibly for push notifications.
#### Agnexus Package
AI agent system for farming queries.
##### Agents
- AdvisoryAgent.java: Provides general farming advice.
- DiagnosisAgent.java: Diagnoses crop diseases/pests using Gemini AI, supports text and image analysis.
- LogAnalysisAgent.java: Analyzes logs for insights.
- MarketAgent.java: Provides market price information.
- NotificationAgent.java: Handles notification-related queries.
- ProactiveInsightAgent.java: Generates proactive insights.
- RouterAgent.java: Routes queries to appropriate agents using Gemini prompt.
##### Controllers
- AgentController.java: REST controller for agent queries at /api/agent/query.
##### Engine
- AgentContext.java: Context for agent execution (userQuery, crop, metadata).
- AgentGraphEngine.java: Orchestrates agent graph execution.
- AgentNode.java: Interface for agent nodes.
- AgentResponse.java: Response from agent execution (response, nextNode, terminal).
##### Services
- AgentEventService.java: Service for agent events.
- GeminiClientService.java: Client for Google Gemini AI, handles text generation and image analysis.
##### Tools
- CropTreatmentTool.java: Tool for crop treatment recommendations.
- LogSearchTool.java: Tool for searching logs.
- MandiPriceTool.java: Tool for mandi (market) prices.
- Tool.java: Base interface for tools.
- ToolResult.java: Result from tool execution.
- WeatherTool.java: Tool for weather information.
##### Utils
- PromptLoader.java: Loads prompts from resources/prompts/.
### src/main/resources/
- pplication.properties: Configuration for Kafka, MongoDB, Redis, email, Firebase, etc.
- prompts/: Text files for AI prompts (diagnosis_prompt.txt, market_prompt.txt, router_prompt.txt).
- 	emplates/: Thymeleaf templates for emails in multiple languages (en/, hn/, kn/, mr/, ta/, te/).
### src/test/java/
- EventApplicationTests.java: Basic Spring Boot test.
- UserTokenServiceTest.java: Tests for UserTokenService.
- NotificationProcessingServiceTest.java: Tests for NotificationProcessingService.
## Key Workflows
- **Event Processing**: Kafka consumers listen to topics, process events asynchronously, apply idempotency and rate limiting, send to providers.
- **AI Query Handling**: POST to /api/agent/query routes to RouterAgent, which uses Gemini to select specialized agent (Diagnosis, Market, etc.).
- **Notification Sending**: Based on sendPush/sendEmail flags, uses providers like Brevo for email or Firebase for push.
- **Logging**: All actions logged to MongoDB via LogService.
- **Error Handling**: Circuit breakers, retries, DLQ for failures.
## Dependencies and Integrations
- **Kafka**: Event streaming with Confluent Cloud.
- **MongoDB**: Data storage for notifications and logs.
- **Redis**: Caching, idempotency, rate limiting.
- **Email**: Brevo API or AWS SES for transactional emails.
- **Push**: Firebase Cloud Messaging.
- **AI**: Google Gemini for text/image analysis.
- **Monitoring**: OpenTelemetry for tracing, Micrometer for metrics.
- **Resilience**: Resilience4j for circuit breakers.
This summary covers the entire project structure and class details for comprehensive understanding.
