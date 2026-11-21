# AGENTS.md
## Architecture
SignalHub is a Spring Boot microservice for event-driven notifications in Agri-Nexus. It consumes Kafka events, processes notifications via providers (Email: Brevo/SES, Push: Firebase), and integrates a production-ready AI agent system with RAG, persistent memory, and multi-provider LLM support.

Key components:
- **Notifications**: Kafka consumers (NotificationConsumer) process events from 'notifications' and 'notification-requests' topics.
- **AI Agent System (AgNexus)**: LangGraph-inspired state-machine engine with 7+ specialized agents, RAG pipeline (MongoDB Atlas Vector Search), persistent conversation memory (Redis L1 + MongoDB L2 + LLM-summary L3), and multi-provider LLM routing (Gemini + Groq) with automatic API key rotation.
- **Data**: MongoDB for notifications/logs/conversations/knowledge base, Redis for cache/idempotency/rate limiting/session memory.
- **External**: Confluent Kafka, Upstash Redis, Atlas MongoDB, Brevo email, Firebase push, Google Gemini API, Groq API.

## AI Agent Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Agent Graph Engine                          │
│  (LangGraph-style state machine with conditional routing)      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Request → Memory Load → RAG Retrieval → RouterAgent            │
│    ├→ DiagnosisAgent (crop disease analysis + image support)    │
│    ├→ MarketAgent (mandi prices + trading recommendations)     │
│    ├→ AdvisoryAgent (weather-aware farming advice)              │
│    ├→ GeneralChatAgent (casual/platform queries)               │
│    ├→ LogAnalysisAgent (microservice log diagnosis)            │
│    └→ NotificationAgent (push notification dispatch)           │
│                                                                 │
│  Final Response → Save to Memory → Return to Caller            │
└─────────────────────────────────────────────────────────────────┘
```

### LLM Layer (`com.khetisetu.event.agnexus.llm`)
- **LLMProvider**: Interface for LLM providers (generate, chat, isAvailable).
- **GeminiProvider**: Google Gemini REST API client (gemini-2.0-flash). Supports multi-turn chat, system instructions, image analysis.
- **GroqProvider**: Groq client (OpenAI-compatible API at api.groq.com/openai/v1). Supports llama-3.3-70b-versatile.
- **APIKeyRotationService**: Manages multiple keys per provider with round-robin rotation. Marks keys as exhausted (60s cooldown) on 429 errors. Uses Redis for distributed tracking.
- **LLMProviderRouter**: Routes calls to best available provider. Primary → Gemini, Fallback → Groq. Automatic key rotation + provider failover.

### Memory Layer (`com.khetisetu.event.agnexus.memory`)
- **Conversation**: MongoDB document — session metadata, summary, message count, status.
- **ConversationMessage**: MongoDB document — individual messages with agent attribution, token usage, RAG references.
- **ConversationMemoryService**: 3-layer memory:
  - L1 Redis: Last 10 messages cached with 30-min TTL (`conv:{id}:messages`).
  - L2 MongoDB: Full persistent history in `conversations` + `conversation_messages` collections.
  - L3 Summary: LLM-generated compressed summary when messages > 20 (stored in `conversation.summary`).

### RAG Pipeline (`com.khetisetu.event.agnexus.rag`)
- **KnowledgeDocument**: MongoDB document with title, content, category, tags, and 768-dim vector embedding.
- **EmbeddingService**: Generates embeddings via Gemini text-embedding-004 model.
- **RAGService**: Vector similarity search (Atlas $vectorSearch) with text search fallback. Builds augmented context for LLM prompts.
- Categories: CROP_DISEASE, FARMING_PRACTICE, MARKET_INFO, GOVERNMENT_SCHEME, PEST_CONTROL, SOIL_HEALTH, WEATHER_ADVISORY, GENERAL.

### Engine (`com.khetisetu.event.agnexus.engine`)
- **AgentContext**: Full execution context — user query, conversation history, RAG docs, execution trace, graph state, tool results.
- **AgentGraphEngine**: Executes agent graph with max iterations (10), timeout (30s), execution tracing, error fallback to GeneralChatAgent.
- **GraphState**: Enum — ROUTING, PROCESSING, TOOL_CALLING, SYNTHESIZING, COMPLETE, ERROR.

### REST API (`com.khetisetu.event.agnexus.controllers.AgentController`)
- `POST /api/agent/chat`: Main chat endpoint with session-based memory.
- `POST /api/agent/query`: Legacy query endpoint (backward compatible).
- `GET /api/agent/conversations/{userId}`: List user conversations.
- `GET /api/agent/conversations/{conversationId}/messages`: Get conversation history.
- `DELETE /api/agent/conversations/{conversationId}`: Archive conversation.
- `POST /api/agent/knowledge`: Ingest knowledge document with embedding.
- `POST /api/agent/knowledge/bulk`: Bulk ingest.
- `GET /api/agent/knowledge/search?q=...&category=...`: Search knowledge base.
- `DELETE /api/agent/knowledge/{id}`: Delete document.
- `GET /api/agent/health`: System health (providers, agents, conversations, KB size).

## Workflows
- **Build**: mvn clean package (Docker multi-stage build).
- **Run**: mvn spring-boot:run or java -jar target/event-0.0.1-SNAPSHOT.jar.
- **Test**: python test_producer.py sends test events to Kafka.
- **AI Test Dashboard**: Admin panel at /admin/ai-agents for interactive agent testing, execution tracing, and KB management.
- **Debug**: Use MDC logging (traceId, eventId, userId, type) for tracing.

## Patterns
- **Idempotency**: Redis keys 'idempotency:notif:%s' prevent duplicates (24h TTL).
- **Rate Limiting**: Redis 'rate:notif:%s:%s' (5/min per user/type).
- **API Key Rotation**: Round-robin across multiple keys per LLM provider. Auto-rotate on 429 with 60s cooldown. Redis-tracked for distributed systems.
- **LLM Failover**: Primary provider (Gemini) → key rotation → fallback provider (Grok).
- **3-Layer Memory**: Redis L1 (hot cache, 30min TTL) → MongoDB L2 (persistent) → LLM Summary L3 (compressed context).
- **RAG Enrichment**: Vector search → text search fallback → tag search fallback.
- **Async Processing**: @Async with @Retryable (4 attempts, exponential backoff).
- **Error Handling**: Circuit breakers (Resilience4j), logs to MongoDB, analytics to Kafka. Agent errors fallback to GeneralChatAgent.
- **Templates**: Thymeleaf multi-language emails in src/main/resources/templates/{en,hi,kn,mr,ta,te}/.
- **Execution Tracing**: Every agent step recorded with agent name, action, detail, and duration for debugging via admin dashboard.

## Key Files
- `NotificationProcessingService.java`: Core notification logic, processes requests, sends to providers.
- `AgentGraphEngine.java`: LangGraph-style graph executor with memory/RAG integration, execution tracing.
- `LLMProviderRouter.java`: Multi-provider router with automatic failover and key rotation.
- `ConversationMemoryService.java`: 3-layer memory management (Redis + MongoDB + LLM summary).
- `RAGService.java`: Vector similarity search + text fallback for knowledge retrieval.
- `RouterAgent.java`: Routes queries to specialized agents using LLM analysis.
- `AgentController.java`: REST API for chat, conversations, knowledge base, and health.
- `application.properties`: Config for Kafka, MongoDB, Redis, LLM keys, RAG, memory, agent settings.
- `pom.xml`: Dependencies (Spring Boot 3.5.7, Kafka, MongoDB, Redis, OkHttp, JSON).
- `src/app/admin/ai-agents/page.js`: Admin dashboard AI agent testing console.

### Configuration Properties (application.properties)
```properties
# LLM Providers
llm.primary.provider=gemini
llm.gemini.api.keys=${GEMINI_API_KEYS:...}
llm.gemini.model=gemini-2.0-flash
llm.grok.api.keys=${GROK_API_KEYS:}
llm.grok.model=grok-3-mini

# Key Rotation
llm.key.rotation.enabled=true
llm.key.rate-limit-per-minute=15

# RAG
rag.embedding.model=text-embedding-004
rag.embedding.dimensions=768
rag.search.top-k=5
rag.search.min-score=0.7

# Memory
memory.conversation.max-messages=50
memory.conversation.summary-threshold=20
memory.redis.session-ttl-minutes=30

# Agent
agent.max-iterations=10
agent.execution-timeout-seconds=30
```

Examples:
- Send notification: Set sendPush=true, sendEmail=true in NotificationRequestEvent.
- Agent query: POST /api/agent/chat with { userId, sessionId, message, crop, location }.
- Add knowledge: POST /api/agent/knowledge with { title, content, category, tags }.
- Health check: GET /api/agent/health returns provider statuses, registered agents, active conversations count.

## Coding Standards
- Follow Java naming conventions (camelCase for variables/methods, PascalCase for classes).
- Use Lombok for boilerplate code (getters/setters, constructors).
- Proper exception handling with custom exceptions (e.g., NotificationException, RateLimitException).
- Use @Service, @Component, @Repository annotations for Spring beans.
- Use latest Java features (records, var, switch expressions) where appropriate.
- Write clean, modular code with single responsibility principle.
- Include Javadoc comments for public methods and classes.
- Ensure thread safety in async processing and shared resources (e.g., Redis).
- Use configuration properties for external configs (LLM keys, RAG settings, memory config).
- Give meaningful method names and variable names for readability.
- Use logService.storeLog() for all successful and failed notifications.

## Knowledge Graph
- **Entities**: Notification, User, Event, Agent, Query, Log, Conversation, KnowledgeDocument.
- **Relationships**: User sends Notification, Notification is triggered by Event, Agent processes Query, Log records Notification status, Conversation contains Messages, KnowledgeDocument augments Agent responses.
- **Attributes**: Notification (id, type, content, status), User (id, name, contact), Event (id, type, payload), Agent (id, name, specialty), Query (id, userQuery, crop, metadata), Log (id, eventId, userId, type, status, errorMessage), Conversation (id, userId, sessionId, summary), KnowledgeDocument (id, title, content, category, embedding).

## Documentation
- **README.md**: Overview, setup instructions, usage examples.
- **AGENTS.md**: Architecture, AI system design, workflows, patterns, key files, coding standards.
- **INFORMATION.md**: Integration details, feature summary.
- Update AGENTS.md with any new architectural changes, workflows, patterns, or coding standards as the project evolves.
- Ensure all new features or changes are reflected in the documentation for consistency.
- Regularly review and update AGENTS.md to ensure it remains accurate and useful for AI coding agents working on the codebase.
