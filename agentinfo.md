# SignalHub AI Agent Architecture (AgNexus) - Interview Guide

This guide provides a comprehensive, in-depth explanation of the AI Agent System (AgNexus) integrated within the SignalHub microservice. It is designed to prepare you for technical interviews by covering the "What", "Why", and "How" of the system from the ground up.

---

## 1. High-Level Architecture: What is it?

SignalHub is a Spring Boot microservice handling event-driven notifications. Embedded within it is **AgNexus**, a LangGraph-inspired state-machine engine that processes farming queries using a multi-agent AI architecture. 

The system takes a user's query, enriches it with contextual knowledge (RAG) and past conversations (Memory), routes it to the most capable specialized agent, executes necessary tools, and synthesizes a response.

### Why did we build it this way?
- **Modularity**: By separating concerns into specialized agents (e.g., Diagnosis, Market, Advisory), we can update or add agents without breaking the core system.
- **Resilience & Failover**: Multi-provider LLM support ensures that if one provider (e.g., Google Gemini) fails or rate-limits, the system falls back to another (e.g., Groq).
- **Context Awareness**: A 3-layer persistent memory combined with Retrieval-Augmented Generation (RAG) ensures the AI understands the user's history and agricultural domain context.

---

## 2. Core Components: How does it work?

### A. The Agent Graph Engine (LangGraph-style)
The engine operates as a state machine. It manages the execution flow across different components:

1. **Request Intake**: User query is received via `POST /api/agent/chat`.
2. **Memory Load**: Historical context is fetched from the L1 (Redis) or L2 (MongoDB) layers.
3. **RAG Retrieval**: The query is vectorized to fetch relevant farming/disease data from MongoDB Atlas.
4. **Routing**: The `RouterAgent` decides which specialized agent is best suited.
5. **Execution**: The chosen agent processes the query, optionally using external tools (e.g., `WeatherTool`, `MandiPriceTool`).
6. **Synthesis**: The final response is generated, saved back to memory, and returned.

*States Managed:* `ROUTING`, `PROCESSING`, `TOOL_CALLING`, `SYNTHESIZING`, `COMPLETE`, `ERROR`.

### B. Specialized Agents
Each agent has a specific system prompt and access to distinct tools:
- **RouterAgent**: The brain that routes queries to specialized agents.
- **DiagnosisAgent**: Diagnoses crop diseases and pests. Supports image analysis via Gemini.
- **MarketAgent**: Provides mandi prices and trading recommendations using `MandiPriceTool`.
- **AdvisoryAgent**: Gives weather-aware farming advice using `WeatherTool`.
- **LogAnalysisAgent**: Diagnoses microservice logs for system insights using `LogSearchTool`.
- **NotificationAgent**: Handles user requests to configure push notifications.
- **GeneralChatAgent**: Handles casual queries and serves as a fallback for errors.

### C. The 3-Layer Memory System (`ConversationMemoryService`)
To provide context without blowing up the context window (token limit) or causing excessive latency:
1. **L1 (Hot Cache) - Redis**: Stores the last 10 messages with a 30-minute TTL. Ultra-fast access for active sessions.
2. **L2 (Persistent) - MongoDB**: Stores the full history in `conversations` and `conversation_messages` collections.
3. **L3 (Summarized) - LLM Summary**: When a conversation exceeds 20 messages, the LLM compresses the history into a summary string to save tokens on future requests.

### D. Multi-Provider LLM Layer (`LLMProviderRouter`)
To guarantee high availability and manage rate limits:
- **Primary Provider**: Google Gemini (`gemini-2.0-flash`).
- **Fallback Provider**: Groq (`llama-3.3-70b-versatile`).
- **API Key Rotation**: The `APIKeyRotationService` tracks multiple keys per provider in Redis. If a `429 Too Many Requests` occurs, it marks the key exhausted for a 60s cooldown and seamlessly rotates to the next key. If all primary keys fail, it fails over to the Groq provider.

### E. Retrieval-Augmented Generation (RAG) Pipeline
To ground the AI in factual agricultural data:
1. **Ingestion**: Documents are embedded using Gemini's `text-embedding-004` (768 dimensions) and stored in MongoDB Atlas.
2. **Retrieval**: Uses Atlas `$vectorSearch` for similarity search.
3. **Fallback Mechanism**: If vector search yields low confidence, it falls back to text search, then tag search.

---

## 3. Workflow & Data Flow Examples

### Example: A Farmer asks "Why are my tomato leaves turning yellow?"
1. **Controller**: Receives POST request with text and optionally an image.
2. **Memory**: Loads previous chat (maybe the farmer mentioned planting tomatoes a month ago).
3. **RAG**: Searches for "tomato yellow leaves" in the Knowledge Base.
4. **Router**: Analyzes intent -> Routes to `DiagnosisAgent`.
5. **Agent Execution**: `DiagnosisAgent` reads RAG data, analyzes the image (if provided), and might call the `CropTreatmentTool`.
6. **Response**: Suggests a treatment plan for Early Blight or nutrient deficiency.
7. **Traceability**: Every step is logged with MDC (Mapped Diagnostic Context) trace IDs and stored via `LogService`.

---

## 4. Key Patterns & Interview Talking Points

If asked *why* you used certain patterns, here is your arsenal:

- **Idempotency & Rate Limiting**: "We used Redis to ensure that duplicate webhook events or rapid user clicks don't trigger the same heavy LLM process twice within 24 hours, and limited requests to 5/min per user to control costs."
- **Circuit Breakers**: "We used Resilience4j. If an LLM or Notification provider fails continuously, the circuit opens to prevent cascading network failures, and we route to a fallback."
- **LangGraph State Machine**: "Rather than a giant monolithic prompt, we built a graph-based state engine. It allows us to inject tools mid-thought, trace execution steps easily (via the Admin Dashboard), and recover gracefully from errors."
- **Key Rotation over Distributed Cache**: "We store LLM API key status in Redis so that across our multiple microservice instances, if one instance gets a 429 rate limit, all instances immediately switch to the next key."

## 5. Setup & Usage

- **Configuration**: Settings like RAG thresholds, LLM keys, and memory limits are managed in `application.properties`.
- **Testing**: We have an **AI Test Dashboard** at `/admin/ai-agents` for interactive testing, monitoring execution traces, and managing the knowledge base.
- **REST APIs**:
  - `POST /api/agent/chat`: Main conversation endpoint.
  - `POST /api/agent/knowledge`: Ingest facts into RAG.
  - `GET /api/agent/health`: Checks system health, active keys, and provider status.

This architecture ensures SignalHub is not just a basic wrapper around OpenAI/Gemini, but a robust, production-ready, highly available enterprise AI agent system.
