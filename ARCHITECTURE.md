# Occasion Predictor AI — Complete Architecture Guide

> End-to-end explanation of every layer: RAG, Redis, Kafka, LLM, database, retry,
> caching, and the wiring that connects them.

---

## Table of Contents

1. [What the Service Does](#1-what-the-service-does)
2. [System Architecture Overview](#2-system-architecture-overview)
3. [Technology Choices](#3-technology-choices)
4. [Database Layer](#4-database-layer)
5. [RAG Pipeline — Knowledge Base to Vector Store](#5-rag-pipeline)
6. [Prediction Workflow — LLM + Validation](#6-prediction-workflow)
7. [Redis Layer — Caching and Rate Limiting](#7-redis-layer)
8. [Kafka Layer — Event-Driven Pipeline](#8-kafka-layer)
9. [Complete Request Walkthrough](#9-complete-request-walkthrough)
10. [Failure and Retry Flows](#10-failure-and-retry-flows)
11. [Configuration Reference](#11-configuration-reference)
12. [Package Structure](#12-package-structure)

---

## 1. What the Service Does

Given a gift order (who it's for, what product, the date, any message), the service
predicts which **gift occasion** it belongs to — Birthday, Mother's Day, Christmas, etc.

```
Input:  orderId, recipientName, recipientRelation, productName,
        productCategory, orderDate, giftMessage

Output: predictedOccasion, confidenceScore, reason, evidence[]
```

This prediction drives downstream systems: reminder scheduling, customer
notifications, analytics, and feedback collection — all decoupled via Kafka.

---

## 2. System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENT (HTTP)                                │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ POST /api/v1/occasion/predictions
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     SPRING BOOT APP  (port 8081)                    │
│                                                                     │
│  ┌──────────────────┐     ┌──────────────────────────────────────┐  │
│  │ OccasionController│────►│       PredictionServiceImpl          │  │
│  │ (rate limiter)   │     │  (orchestrates the full flow)        │  │
│  └──────────────────┘     └──────────────┬───────────────────────┘  │
│                                          │                          │
│         ┌────────────────────────────────┼──────────────────────┐   │
│         │                               │                       │   │
│         ▼                               ▼                       ▼   │
│  ┌─────────────┐            ┌───────────────────┐    ┌────────────┐ │
│  │  RAG Layer  │            │  LLM Workflow     │    │  Kafka     │ │
│  │ (pgvector)  │            │  (Ollama)         │    │  Publisher │ │
│  └──────┬──────┘            └─────────┬─────────┘    └─────┬──────┘ │
│         │                             │                    │        │
└─────────┼─────────────────────────────┼────────────────────┼────────┘
          │                             │                    │
          ▼                             ▼                    ▼
  ┌──────────────┐            ┌──────────────────┐  ┌──────────────┐
  │  PostgreSQL  │            │  Ollama (local)  │  │    Kafka     │
  │  + pgvector  │            │  llama3          │  │  (KRaft)     │
  └──────────────┘            │  nomic-embed-text│  └──────┬───────┘
          │                   └──────────────────┘         │
          ▼                                                 ▼
  ┌──────────────┐                              ┌─────────────────────┐
  │    Redis     │                              │  Kafka Consumers    │
  │  (cache +    │                              │  Reminder           │
  │  rate limit) │                              │  Notification       │
  └──────────────┘                              │  Analytics          │
                                                │  Feedback           │
                                                │  Retry / DLQ        │
                                                └─────────────────────┘
```

---

## 3. Technology Choices

| Technology | Why |
|---|---|
| **Ollama (llama3)** | Runs LLM locally — no API cost, no data leaving the machine |
| **nomic-embed-text** | Fast 768-dim embedding model, free, runs via Ollama |
| **pgvector** | PostgreSQL extension for vector similarity search — single DB for relational + vector data |
| **Spring AI 1.0.3** | Abstracts Ollama chat + embedding + pgvector behind uniform APIs |
| **Redis** | Sub-millisecond cache for idempotency and RAG retrieval; atomic INCR for rate limiting |
| **Kafka (KRaft)** | Durable async bus so prediction results fan out to multiple consumers independently |
| **Flyway** | Single migration file keeps schema reproducible |

---

## 4. Database Layer

### 4.1 Schema

All tables are created by `V1__init.sql` in a single Flyway migration.

```
┌───────────────────────┐        ┌───────────────────────┐
│    prompt_versions    │        │    model_versions     │
│───────────────────────│        │───────────────────────│
│ id          UUID PK   │        │ id          UUID PK   │
│ version     VARCHAR   │        │ model_name  VARCHAR   │
│ template    TEXT      │        │ model_version VARCHAR │
│ description VARCHAR   │        │ provider    VARCHAR   │
│ active      BOOLEAN   │        │ active      BOOLEAN   │
│ created_at  TIMESTAMP │        │ created_at  TIMESTAMP │
└──────────┬────────────┘        └──────────┬────────────┘
           │                                │
           │ FK                             │ FK
           ▼                                ▼
┌──────────────────────────────────────────────────────────────┐
│                         predictions                          │
│──────────────────────────────────────────────────────────────│
│ id                UUID PK                                    │
│ order_id          VARCHAR       ← the gift order ID          │
│ recipient_name    VARCHAR                                    │
│ recipient_relation VARCHAR                                   │
│ product_name      VARCHAR                                    │
│ product_category  VARCHAR                                    │
│ order_date        VARCHAR                                    │
│ gift_message      TEXT                                       │
│ predicted_occasion VARCHAR      ← BIRTHDAY, CHRISTMAS, etc. │
│ confidence_score  DOUBLE        ← 0.0 – 1.0                 │
│ reason            TEXT          ← one sentence              │
│ evidence          TEXT          ← JSON array of signals     │
│ prediction_source VARCHAR       ← e.g. OLLAMA_llama3        │
│ prompt_version_id UUID FK                                    │
│ model_version_id  UUID FK                                    │
│ created_at        TIMESTAMP                                  │
└─────────────────────────────┬────────────────────────────────┘
                              │
                              │ FK (one prediction → one audit row)
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                       prediction_audit                       │
│──────────────────────────────────────────────────────────────│
│ id               UUID PK                                     │
│ prediction_id    UUID FK                                     │
│ raw_prompt       TEXT    ← the exact prompt sent to Ollama   │
│ raw_response     TEXT    ← the exact JSON string Ollama returned │
│ model_parameters TEXT    ← temperature, top-k, top-p, etc.  │
│ rag_chunk_ids    TEXT    ← JSON array of pgvector row IDs used │
│ latency_ms       BIGINT  ← LLM call duration                │
│ created_at       TIMESTAMP                                   │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                        vector_store                          │
│──────────────────────────────────────────────────────────────│
│ id        UUID PK                                            │
│ content   TEXT          ← the text chunk                     │
│ metadata  JSON          ← docName, section, category, etc.  │
│ embedding vector(768)   ← nomic-embed-text output           │
└──────────────────────────────────────────────────────────────┘
```

**Index:** `vector_store_embedding_idx` — HNSW index on `embedding` using cosine
distance, built at migration time. HNSW allows approximate nearest-neighbour search
in O(log n) instead of O(n).

### 4.2 Seed Data (inserted by V1)

- `prompt_versions`: one row, version `v1` — the RAG-aware system instructions prompt
- `model_versions`: one row — `llama3 / latest / OLLAMA`

---

## 5. RAG Pipeline

RAG = **Retrieval-Augmented Generation**. Instead of relying solely on the LLM's
training data, we embed a curated knowledge base into pgvector and retrieve the most
relevant chunks for each prediction request. The LLM then uses those chunks as
grounding context.

### 5.1 Knowledge Base Documents

Five documents live in `src/main/resources/rag-docs/`:

| File | What it contains |
|---|---|
| `occasion_rules.md` | Per-occasion keyword signals and date windows (e.g. "roses + February = Valentine's") |
| `gift_category_rules.md` | Product category → occasion confidence boosts (e.g. "jewellery → Anniversary +0.3") |
| `recipient_relation_rules.md` | Recipient relation → primary occasion (e.g. "Mother → MOTHERS_DAY") |
| `brand_rules.md` | Brand → product category + peak occasion (e.g. "Pandora → Jewellery → Anniversary") |
| `holiday_calendar.json` | US/UK holiday dates with lead-time windows (e.g. Christmas: Dec 25, buy window Nov 15–Dec 24) |

### 5.2 Ingestion Flow (startup, runs once)

```
RagIngestionService.run()        ← ApplicationRunner, fires on startup
        │
        ├─► SELECT COUNT(*) FROM vector_store
        │       > 0  → skip (already ingested)
        │       = 0  → ingest
        │
        ▼
RagDocumentLoader.loadAll()
        │
        ├─► loadMarkdown("occasion_rules.md")
        │       Split on "\n## " headings → sections
        │       Each section → chunk(text, 800 chars, 100 overlap)
        │       Attach metadata: { docName, category, section }
        │
        ├─► loadMarkdown("gift_category_rules.md")   (same pattern)
        ├─► loadMarkdown("recipient_relation_rules.md")
        ├─► loadMarkdown("brand_rules.md")
        │
        └─► loadHolidayCalendar("holiday_calendar.json")
                Each JSON entry → one Document
                Metadata: { docName, country, ruleId }

        ↓  List<Document>  (≈ 30–50 chunks total)

VectorStore.add(documents)
        │
        ▼
Ollama nomic-embed-text
        │  Each chunk text → 768-dim float vector
        ▼
INSERT INTO vector_store (content, metadata, embedding)
        FOR each chunk
```

**Chunking logic:** `chunk(text, size=800, overlap=100)` — splits at sentence
boundaries (`. ` or `\n`) that keep chunks under 800 chars, with the last 100 chars
of each chunk prepended to the next (overlap preserves context across boundaries).

### 5.3 Retrieval Flow (per prediction)

```
PredictionRequest (orderId, relation, product, category, date, message)
        │
        ▼
RagRetriever.retrieve(request)
        │
        ├─► buildQuery(request)
        │       → "Recipient relation: Mother. Product: Rose Bouquet.
        │          Category: Flowers. Order date: 2025-05-08.
        │          Gift message: Happy Mother's Day Mum!"
        │
        ├─► hashQuery(query)  → SHA-256 hex string
        │
        ├─► Redis GET rag:{hash}
        │       HIT  → deserialise List<CachedChunk> → List<Document>
        │              (skips embedding + vector search entirely)
        │
        │       MISS ↓
        │
        ├─► Ollama nomic-embed-text.embed(query)
        │       → 768-dim query vector
        │
        ├─► pgvector cosine similarity search
        │       SELECT * FROM vector_store
        │       ORDER BY embedding <=> query_vector
        │       LIMIT 5 (top-k)
        │       WHERE similarity >= 0.5 (threshold)
        │
        ├─► Redis SET rag:{hash}  (TTL 30 min)
        │
        └─► return List<Document>  (up to 5 chunks)
```

### 5.4 Prompt Assembly

`RagPromptBuilder.build(systemInstructions, chunks, request)` assembles:

```
[System Instructions from prompt_versions.template]

## Retrieved Context Rules:

### Rule 1 — Birthday Signals:
<chunk text from occasion_rules.md>

### Rule 2 — Mother's Day Window:
<chunk text from holiday_calendar.json>

... (up to 5 rules)

## Order Details:
- Recipient Name: Sarah
- Relation: Mother
- Product: Luxury Rose Bouquet (Flowers)
- Order Date: 2025-05-08
- Gift Message: Happy Mother's Day Mum! Love you always.
```

This full string is sent to Ollama as the user message.

---

## 6. Prediction Workflow

### 6.1 LLM Call

`PredictionWorkflow.call(prompt)` uses Spring AI `ChatClient`:

```java
chatClient.prompt()
    .user(prompt)
    .call()
    .content()   // returns the raw string from Ollama
```

Configured with deterministic settings:
- `temperature: 0.1` — nearly deterministic (low randomness)
- `top-k: 40` — sample from top 40 tokens
- `top-p: 0.9` — nucleus sampling
- `num-predict: 200` — max output tokens

### 6.2 Response Parsing and Validation

The LLM is instructed to return only this JSON:

```json
{
  "occasion": "MOTHERS_DAY",
  "confidence": 0.95,
  "reason": "One sentence explanation.",
  "evidence": ["signal 1", "signal 2"]
}
```

`PredictionWorkflow.parseAndValidate(rawContent)` does:

```
1. Strip markdown fences (```json ... ```)
2. Jackson parse → Map<String, Object>
3. Validate occasion:  must be one of the 8 allowed enum values
                       invalid → throw (fallback to UNKNOWN)
4. Validate confidence: must be 0.0 – 1.0
                        out of range → clamp or fallback
5. Confidence threshold: < 0.4 → force UNKNOWN regardless of occasion
6. Return LlmResult(occasion, confidence, reason, evidence, rawContent, modelParameters)
```

If parsing fails at any step, `fallback()` returns:
```
occasion=UNKNOWN, confidence=0.0, reason="Could not parse LLM response"
```

### 6.3 Audit Trail

Every prediction writes two rows:

- **`predictions`** — the cleaned result (occasion, confidence, reason, evidence)
- **`prediction_audit`** — the raw prompt, raw LLM response, model parameters
  (temperature / top-k / top-p / num-predict as JSON), the pgvector chunk IDs used,
  and the LLM call latency in milliseconds

The chunk IDs in the audit let you replay exactly which knowledge-base rows
contributed to a prediction.

---

## 7. Redis Layer

Redis serves three independent purposes, all using `RedisTemplate<String, String>`
with String serialization (events serialized to JSON by `ObjectMapper`).

### 7.1 Prediction Idempotency Cache

**Purpose:** If the same `orderId` is submitted twice (client retry, duplicate
webhook, etc.), the second request returns the cached result in < 1 ms without
touching Ollama, pgvector, or the DB.

```
Key:   prediction:{orderId}
Value: PredictionResponse JSON
TTL:   86400 s (24 hours)

Flow:
  predict(request)
      │
      ├─► GET prediction:{orderId}
      │       HIT  → return deserialised PredictionResponse immediately
      │       MISS → run full flow, then SET prediction:{orderId}
      │
      └─► (after LLM + save) SET prediction:{orderId}  TTL 24 h
```

### 7.2 RAG Retrieval Cache

**Purpose:** The embedding call + pgvector HNSW search takes ~200–500 ms. For the
same combination of recipient relation / product / category / date / message, the
result is always identical. Caching the chunks avoids re-embedding on repeated
similar queries.

```
Key:   rag:{sha256(queryString)}
Value: List<CachedChunk> JSON
TTL:   1800 s (30 minutes)

CachedChunk stores:
  - id          ← original pgvector row UUID
  - text        ← chunk content
  - metadata    ← { section, category, docName, ... }
  - _chunk_id   ← original ID preserved in metadata for accurate audit logging
                  (reconstructed Documents get a new UUID from Spring AI, so
                   we store the original in metadata to keep audit records correct)
```

### 7.3 Rate Limiter

**Purpose:** Prevent a single client from flooding the LLM endpoint.

```
Key:   rate:{clientIp}:{minuteEpoch}    e.g. rate:127.0.0.1:28455743
Value: request count (integer as string)
TTL:   60 s (auto-expires after the minute window)

Algorithm: Fixed-window counter
  1. INCR rate:{ip}:{minute}       → count
  2. If count == 1: EXPIRE key 60  → first request in this window, set expiry
  3. If count > 20: throw RateLimitExceededException → HTTP 429

Fail-open: if Redis is unreachable, the request is allowed through.
```

Client IP is read from `X-Forwarded-For` header (first entry) or
`HttpServletRequest.getRemoteAddr()` as fallback.

---

## 8. Kafka Layer

### 8.1 Why Kafka Here

After the prediction is saved to the DB, multiple systems need to act on it:
- Schedule a gift reminder for a future date
- Send a "we predicted your occasion" notification to the customer
- Record the prediction for analytics
- Queue a feedback request to validate the prediction

Without Kafka, `PredictionServiceImpl` would need to call all these systems directly,
creating tight coupling and making prediction latency include all their response times.

With Kafka, the prediction service publishes one event and returns. Each consumer
processes it independently, at its own pace, with its own retry policy.

### 8.2 Infrastructure

**KRaft mode** — Kafka 3.7 without ZooKeeper. A single combined broker+controller
node runs in development. The `KafkaConfig` bean builds the producer and consumer
factories directly from `KafkaProperties`, bypassing Spring Boot auto-configuration
to keep type safety clean (`<String, String>` throughout).

All events are serialized to JSON strings by `ObjectMapper` in `PredictionEventPublisher`
and deserialized by `ObjectMapper` in each consumer. This avoids Spring Kafka's
`JsonDeserializer` type-header complexity.

### 8.3 Topics

| Topic | Partitions | Purpose |
|---|---|---|
| `prediction.requested` | 1 | Published when prediction workflow starts — audit trail and replay source |
| `occasion.predicted` | 3 | Published after successful DB save — 3 partitions so 4 consumer groups scale independently |
| `prediction.failed` | 1 | Published when LLM call throws an exception |
| `prediction.retry` | 1 | Intermediate hops between retry attempts |
| `prediction.dlq` | 1 | Permanent failure — all retry attempts exhausted |

Topics are created automatically by `KafkaTopicConfig` (`NewTopic` beans) on first
app startup.

### 8.4 Event Records

```
PredictionRequestedEvent
  eventId, orderId, recipientName, recipientRelation,
  productName, productCategory, orderDate, giftMessage, occurredAt

OccasionPredictedEvent
  eventId, orderId, predictionId (DB UUID), occasion, confidence,
  reason, evidence[], predictionSource, occurredAt

PredictionFailedEvent
  eventId, orderId, errorMessage, attemptCount,
  originalRequest (PredictionRequestedEvent embedded), occurredAt
```

`PredictionFailedEvent` embeds the full original request so `RetryConsumer` can
replay the prediction without a DB lookup.

### 8.5 Consumers

```
occasion.predicted
        │
        ├──► ReminderSchedulingConsumer  (reminder-scheduling-group)
        │       Schedules a gift reminder for a future date based on the occasion
        │
        ├──► NotificationConsumer  (notification-group)
        │       Sends a "we predicted your occasion" notification to the customer
        │
        ├──► AnalyticsConsumer  (analytics-group)
        │       Records occasion, confidence, and source metrics for reporting
        │
        └──► FeedbackConsumer  (feedback-group)
                Queues a delayed feedback-collection job post-delivery


Each consumer group reads independently from the same topic partition set.
A failure in one consumer (e.g. NotificationConsumer) does not affect
ReminderSchedulingConsumer — they have separate offsets and separate retry states.
```

### 8.6 Consumer Error Handling (shared)

`KafkaConfig` configures a single `ConcurrentKafkaListenerContainerFactory` with a
`DefaultErrorHandler` + `DeadLetterPublishingRecoverer`:

```
Consumer method throws exception
        │
        ├─► Retry 1 (after 2 s)
        ├─► Retry 2 (after 2 s)
        │
        └─► Exhausted → DeadLetterPublishingRecoverer
                            → publish raw message to prediction.dlq
```

This protects all `occasion.predicted` consumers. If a downstream service is down,
messages accumulate in the DLQ for manual or automated replay.

### 8.7 Prediction Retry Flow

When the LLM call itself fails (Ollama offline, timeout, parse error):

```
PredictionServiceImpl.predict() throws
        │
        └─► PredictionEventPublisher.publishPredictionFailed(request, errorMessage)
                    → Kafka: prediction.failed
                             { orderId, errorMessage, attemptCount=0, originalRequest }

RetryConsumer listens to [prediction.failed, prediction.retry]
        │
        ├─► Deserialise PredictionFailedEvent
        ├─► attempt = event.attemptCount() + 1
        │
        ├─► If attempt > maxAttempts (3):
        │       → publish to prediction.dlq
        │       → return
        │
        ├─► Apply exponential backoff: sleep( min(2000 × 2^(attempt-1), 30000) ms )
        │       attempt 1: sleep 2 s
        │       attempt 2: sleep 4 s
        │       attempt 3: sleep 8 s
        │
        ├─► predictionService.predict(toRequest(event))
        │       SUCCESS → new prediction saved + occasion.predicted published → done
        │       FAIL    →
        │
        └─► publishPredictionFailed(originalRequest, errorMessage, attempt)
                    → Kafka: prediction.retry
                             { orderId, errorMessage, attemptCount=attempt, originalRequest }
                    (RetryConsumer picks this up again in the next poll)
```

### 8.8 DLQ Consumer

`DlqConsumer` reads from `prediction.dlq`. In its current form it logs the failure
and the error message. Production extension points:
- Send PagerDuty / Slack alert
- Persist to a `failed_predictions` table for ops review
- Expose a `/admin/dlq` endpoint to list and manually replay

### 8.9 Replay Endpoint

```
POST /api/v1/occasion/admin/replay/{orderId}

1. Find the most recent Prediction row for the given orderId in the DB
2. Reconstruct PredictionResponse from stored fields
3. Publish OccasionPredictedEvent to occasion.predicted
4. Return 202 Accepted

Use case: a consumer crashed and missed the original event,
or the downstream service was down. Replay re-triggers all consumers
without re-invoking the LLM — the stored prediction result is reused.
```

---

## 9. Complete Request Walkthrough

This traces a single `POST /api/v1/occasion/predictions` for a new `orderId`
with a cold Redis cache.

```
Step  Actor                   What happens
────  ──────────────────────  ────────────────────────────────────────────────────
 1    OccasionController      Receives HTTP POST, extracts client IP from
                              X-Forwarded-For or RemoteAddr

 2    RateLimiterService      INCR rate:127.0.0.1:28455743 in Redis
                              Count = 1 → EXPIRE key 60s → allowed through

 3    PredictionServiceImpl   Calls cacheService.getCachedPrediction("ORD-001")
                              GET prediction:ORD-001 → nil (cache miss)

 4    PredictionEventPublisher  Publish PredictionRequestedEvent
                              → Kafka topic: prediction.requested
                              (fire-and-forget, does not block)

 5    PromptVersionRepository   SELECT * FROM prompt_versions WHERE active = true
                              → returns the v1 RAG-aware system instructions

 6    ModelVersionRepository    SELECT * FROM model_versions WHERE active = true
                              → returns llama3 / latest / OLLAMA

 7    RagRetriever            buildQuery: "Recipient relation: Mother. Product: Rose
                              Bouquet. Category: Flowers. Order date: 2025-05-08.
                              Gift message: Happy Mother's Day Mum!"

 8    PredictionCacheService  SHA-256 hash of query string
                              GET rag:{hash} → nil (cache miss)

 9    Ollama (nomic-embed-text)  HTTP POST to localhost:11434/api/embeddings
                              → 768-dim float vector [0.021, -0.043, ...]

10    pgvector                SELECT id, content, metadata FROM vector_store
                              ORDER BY embedding <=> '[0.021,-0.043,...]'::vector
                              LIMIT 5 WITH similarity >= 0.5
                              → 5 chunks returned

11    PredictionCacheService  SET rag:{hash} = [chunk1, chunk2, ...] JSON
                              EXPIRE 1800

12    RagPromptBuilder        Assembles:
                              - System instructions (from step 5)
                              - "## Retrieved Context Rules:" + 5 chunk texts
                              - "## Order Details:" + all request fields

13    PredictionWorkflow      ChatClient.prompt(ragPrompt).call()
                              → HTTP POST to localhost:11434/api/chat
                              → Ollama llama3 generates JSON response
                              ← '{"occasion":"MOTHERS_DAY","confidence":0.95,
                                  "reason":"...","evidence":[...]}'

14    PredictionWorkflow      parseAndValidate:
                              - Strip markdown fences (none here)
                              - Parse JSON
                              - Validate occasion → MOTHERS_DAY ✓ (in enum)
                              - Validate confidence → 0.95 ✓ (in [0,1])
                              - Threshold check → 0.95 >= 0.4 ✓ (keep as-is)
                              → LlmResult(MOTHERS_DAY, 0.95, "...", [...])

15    PredictionRepository    INSERT INTO predictions (...) VALUES (...)
                              → prediction.id = UUID-abc

16    PredictionAuditRepository  INSERT INTO prediction_audit (...)
                              raw_prompt = <full prompt string>
                              raw_response = '{"occasion":"MOTHERS_DAY",...}'
                              model_parameters = '{"temperature":0.1,...}'
                              rag_chunk_ids = '["uuid1","uuid2","uuid3","uuid4","uuid5"]'
                              latency_ms = 4823

17    PredictionCacheService  SET prediction:ORD-001 = PredictionResponse JSON
                              EXPIRE 86400

18    PredictionEventPublisher  Publish OccasionPredictedEvent
                              → Kafka topic: occasion.predicted
                              Key: "ORD-001"

19    OccasionController      Return HTTP 200 with PredictionResponse JSON

(async, after HTTP response)

20    ReminderSchedulingConsumer  Consume from occasion.predicted
                              Log: "scheduling reminder for ORD-001 MOTHERS_DAY"

21    NotificationConsumer    Consume from occasion.predicted
                              Log: "sending notification for ORD-001"

22    AnalyticsConsumer       Consume from occasion.predicted
                              Log: "recording metrics for ORD-001 confidence=0.95"

23    FeedbackConsumer        Consume from occasion.predicted
                              Log: "queuing feedback for prediction UUID-abc"
```

**Second call with same orderId (cache hit):**

```
Step  Actor                   What happens
────  ──────────────────────  ───────────────────────────────────────────────────
 1    OccasionController      Receives HTTP POST ORD-001

 2    RateLimiterService      INCR rate:127.0.0.1:... → count = 2 → allowed

 3    PredictionServiceImpl   GET prediction:ORD-001 → HIT
                              Deserialise PredictionResponse → return immediately

     Total time: < 5 ms (no LLM, no DB read, no vector search)
```

---

## 10. Failure and Retry Flows

### 10.1 Ollama Is Offline

```
predictionWorkflow.call(prompt)
        → HTTP connection refused (localhost:11434)
        → Spring AI throws exception

PredictionServiceImpl.predict() catch block:
        → log.error(...)
        → publishPredictionFailed(request, "Connection refused")
        → throw e

HTTP response: 500 Internal Server Error

Kafka: prediction.failed published
        → RetryConsumer picks up
        → attempt 1: sleep 2s → predict() → still fails → publish prediction.retry (attempt=1)
        → attempt 2: sleep 4s → predict() → still fails → publish prediction.retry (attempt=2)
        → attempt 3: sleep 8s → predict() → still fails → publish prediction.dlq (attempt=3)
        → DlqConsumer: log error "Prediction permanently failed for ORD-001"
```

### 10.2 Redis Is Offline

All Redis calls in `PredictionCacheService` and `RateLimiterService` are wrapped in
`try-catch`. If Redis is unreachable:
- Rate limiter: logs a warning and **allows the request through** (fail-open)
- Idempotency cache: logs a warning, skips cache, runs full prediction flow
- RAG cache: logs a warning, skips cache, runs full vector search

The service remains fully functional without Redis, just slower.

### 10.3 Consumer Processing Failure

```
ReminderSchedulingConsumer.handle() throws RuntimeException
        (e.g. reminder scheduler service is down)

DefaultErrorHandler:
        → retry attempt 1 (after 2 s)
        → retry attempt 2 (after 2 s)
        → exhausted

DeadLetterPublishingRecoverer:
        → publish original message to prediction.dlq

Other consumers (NotificationConsumer, AnalyticsConsumer, FeedbackConsumer)
are NOT affected — they have independent offsets and continue processing.

DlqConsumer: logs the raw message for investigation.
Replay: once the scheduler is back, POST /admin/replay/ORD-001
        re-publishes OccasionPredictedEvent → all consumers reprocess.
```

---

## 11. Configuration Reference

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/occasion_predictor
    username: postgres
    password: postgres

  jpa:
    hibernate:
      ddl-auto: validate          # Flyway manages schema; JPA just validates

  flyway:
    enabled: true
    baseline-on-migrate: true

  redis:
    host: localhost
    port: 6379

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all                   # wait for all ISR acknowledgements
    consumer:
      auto-offset-reset: earliest # start from beginning on new consumer group
      enable-auto-commit: false   # Spring Kafka manages offset commits

  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: llama3:latest
        options:
          temperature: 0.1        # 0 = fully deterministic, 1 = very random
          top-k: 40               # sample from top 40 token candidates
          top-p: 0.9              # nucleus sampling threshold
          num-predict: 200        # max tokens to generate
      embedding:
        model: nomic-embed-text:latest
    vectorstore:
      pgvector:
        initialize-schema: false  # schema managed by Flyway
        index-type: hnsw
        distance-type: cosine_distance
        dimensions: 768           # must match nomic-embed-text output

app:
  rag:
    chunk-size: 800               # max chars per knowledge chunk
    chunk-overlap: 100            # chars shared between adjacent chunks
    top-k: 5                      # max chunks retrieved per prediction
    similarity-threshold: 0.5     # min cosine similarity (0–1) to include a chunk

  cache:
    prediction-ttl-seconds: 86400 # 24 h — idempotency window per orderId
    rag-ttl-seconds: 1800         # 30 min — RAG retrieval result cache

  rate-limit:
    requests-per-minute: 20       # per client IP, fixed-window counter

  kafka:
    retry:
      max-attempts: 3             # prediction retries before routing to DLQ
      backoff-ms: 2000            # base backoff; doubles each attempt (2s, 4s, 8s)
```

---

## 12. Package Structure

```
src/main/java/org/kishorereddy/occasionpredictor/
│
├── OccasionPredictorApplication.java
│
├── config/
│   ├── FlywayConfig.java          repair() → migrate() strategy
│   ├── KafkaConfig.java           producer/consumer factories + error handler
│   ├── KafkaTopicConfig.java      NewTopic beans (5 topics)
│   ├── OpenApiConfig.java         Swagger/OpenAPI setup
│   └── RedisConfig.java           RedisTemplate<String,String>
│
├── controller/
│   ├── OccasionController.java    POST /predictions, GET /predictions/{id}, GET /health
│   └── ReplayController.java      POST /admin/replay/{orderId}
│
├── consumer/                      Kafka consumers
│   ├── ReminderSchedulingConsumer.java
│   ├── NotificationConsumer.java
│   ├── AnalyticsConsumer.java
│   ├── FeedbackConsumer.java
│   ├── RetryConsumer.java         prediction.failed + prediction.retry → retry logic
│   └── DlqConsumer.java           prediction.dlq → log + alert
│
├── entity/
│   ├── Prediction.java
│   ├── PredictionAudit.java
│   ├── PromptVersion.java
│   ├── ModelVersion.java
│   └── StringListConverter.java   List<String> ↔ TEXT (JPA converter)
│
├── event/
│   ├── Topics.java                topic name constants
│   ├── OccasionPredictedEvent.java
│   ├── PredictionRequestedEvent.java
│   └── PredictionFailedEvent.java
│
├── exception/
│   ├── RateLimitExceededException.java
│   └── GlobalExceptionHandler.java   @RestControllerAdvice → HTTP 429
│
├── model/
│   ├── PredictionRequest.java     @Valid record
│   ├── PredictionResponse.java    response record
│   └── OccasionType.java          enum: BIRTHDAY, ANNIVERSARY, ...
│
├── repository/
│   ├── PredictionRepository.java
│   ├── PredictionAuditRepository.java
│   ├── PromptVersionRepository.java
│   └── ModelVersionRepository.java
│
└── service/
    ├── PredictionService.java     interface
    ├── PredictionWorkflow.java    ChatClient → parse → validate → LlmResult
    ├── RateLimiterService.java    Redis INCR rate limiter
    │
    ├── cache/
    │   ├── PredictionCacheService.java  prediction + RAG chunk cache
    │   └── CachedChunk.java             Document ↔ JSON DTO
    │
    ├── impl/
    │   └── PredictionServiceImpl.java   main orchestrator
    │
    ├── kafka/
    │   └── PredictionEventPublisher.java  serialize + send to Kafka
    │
    └── rag/
        ├── RagDocumentLoader.java    load + chunk knowledge-base docs
        ├── RagIngestionService.java  ApplicationRunner → ingest on startup
        ├── RagRetriever.java         embed query + pgvector search + cache
        └── RagPromptBuilder.java     assemble final LLM prompt
```

---

## 13. Security Layer (Phase 10)

### 13.1 How it fits into the architecture

```
Client
  │
  │  POST /api/v1/occasion/predictions
  │  Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
  │
  ▼
Spring Security Filter Chain
  │
  ├── JwtAuthenticationFilter
  │       │  Reads "Authorization: Bearer <token>" header
  │       │  Calls Keycloak's JWKS endpoint (cached) to get RSA public key
  │       │  Verifies JWT signature with that key
  │       │  Checks: not expired, issuer matches JWT_ISSUER_URI
  │       │  Extracts roles from realm_access.roles or roles claim
  │       └─► Sets SecurityContext with JwtAuthenticationToken
  │
  ├── AuthorizationFilter
  │       Checks SecurityContext against the rules in SecurityConfig:
  │         /admin/**           → ROLE_ADMIN only
  │         POST /predictions   → ROLE_ADMIN or ROLE_SERVICE
  │         GET  /predictions/* → ROLE_ADMIN, ROLE_SERVICE, or ROLE_REVIEWER
  │         /health             → public
  │
  └── Controller method
          Authentication auth  ← injected from SecurityContext
          SecurityAuditService.logAccess(action, resourceId, auth, ip)
                  → INSERT INTO security_audit_log
                  → SECURITY_AUDIT log line
```

When `app.security.enabled=false` (local dev default): the `SecurityFilterChain` is
built with `anyRequest().permitAll()` and the `oauth2ResourceServer` block is never
configured — so no HTTP call to Keycloak is made at startup or at runtime.

### 13.2 Keycloak's role in the flow

Keycloak is the **identity provider (IdP)** — it issues and signs JWTs. The Spring
Boot app is a **resource server** — it only *validates* JWTs; it never issues them.

```
┌──────────────────┐        ┌──────────────────┐        ┌────────────────────┐
│   Client /       │        │   Keycloak        │        │  Spring Boot App   │
│   Service        │        │   (port 8180)     │        │  (port 8081)       │
└────────┬─────────┘        └────────┬──────────┘        └────────┬───────────┘
         │                           │                             │
         │  1. POST /token           │                             │
         │  grant_type=client_credentials                         │
         │  client_id + client_secret                             │
         │─────────────────────────►│                             │
         │                           │                             │
         │  2. access_token (JWT)    │                             │
         │◄─────────────────────────│                             │
         │                           │                             │
         │  3. POST /predictions     │                             │
         │  Authorization: Bearer <JWT>                           │
         │────────────────────────────────────────────────────────►
         │                           │                             │
         │                           │  4. GET /realms/.../certs  │
         │                           │◄───────────────────────────│
         │                           │  (cached after first call) │
         │                           │                             │
         │                           │  5. RSA public key         │
         │                           │──────────────────────────── ►
         │                           │                             │ Verify JWT signature
         │                           │                             │ Check expiry + issuer
         │                           │                             │ Extract roles from JWT
         │                           │                             │
         │  6. 200 PredictionResponse│                             │
         │◄────────────────────────────────────────────────────────
```

Step 4 only happens once (or when Keycloak rotates keys). The JWKS are cached by
the Spring Security `NimbusJwtDecoder` — subsequent requests do not call Keycloak.

### 13.3 JWT structure

Keycloak issues JWTs that look like this (decoded):

**Header:**
```json
{ "alg": "RS256", "kid": "abc123" }
```

**Payload:**
```json
{
  "iss": "http://localhost:8180/realms/occasion-predictor",
  "sub": "service-account-occasion-predictor-api",
  "exp": 1720000000,
  "iat": 1719996400,
  "realm_access": {
    "roles": ["SERVICE", "default-roles-occasion-predictor"]
  },
  "azp": "occasion-predictor-api"
}
```

The app's `SecurityConfig.extractAuthorities()` reads `realm_access.roles`, strips
system roles, and maps each to a `ROLE_` prefixed `GrantedAuthority`:
`SERVICE` → `ROLE_SERVICE`

It also checks a top-level `roles` claim as a fallback for non-Keycloak issuers
(Auth0, Okta, custom OIDC providers).

### 13.4 Token flows

**Client credentials flow** — machine-to-machine (the normal case for this service):
```
Other service  →  POST /token  grant_type=client_credentials  →  Keycloak
               ←  access_token (JWT, typically valid 5 min)   ←
               →  POST /predictions  Authorization: Bearer <JWT>  →  This app
```
No human involved. The `client_id` + `client_secret` identify the caller.

**Resource owner password flow** — for testing with a named human user:
```
Developer  →  POST /token  grant_type=password  username + password  →  Keycloak
           ←  access_token                                           ←
           →  POST /admin/replay/ORD-001  Authorization: Bearer <JWT>  →  This app
```
Only use this flow for dev/testing. Do not use it in production (exposes passwords).

**Authorization code flow** — for a browser-based frontend (not yet implemented):
```
Browser  →  Redirect to Keycloak login page
         ←  User logs in, Keycloak redirects back with auth code
         →  Exchange auth code for tokens
         ←  access_token + refresh_token
         →  API calls with Bearer token
```

### 13.5 Roles and what they protect

```
ADMIN      ─────►  ALL endpoints
                   POST /predictions
                   GET  /predictions/{id}
                   POST /admin/replay/{orderId}

SERVICE    ─────►  POST /predictions              (create from another backend service)
                   GET  /predictions/{id}          (read back a prediction)

REVIEWER   ─────►  GET  /predictions/{id}          (read-only human review)

READ_ONLY  ─────►  GET  /health                    (monitoring / uptime checks)

(no role)  ─────►  GET  /health                    (always public)
                   GET  /swagger-ui/**              (always public)
                   GET  /v3/api-docs/**             (always public)
```

### 13.6 PII masking

Three fields are considered PII and are never logged in plaintext:

| Field | Masking rule | Example |
|---|---|---|
| `recipientName` | First + last char visible | `Sarah` → `S***h` |
| `giftMessage` | Fully suppressed, length retained | `[REDACTED:42chars]` |
| RAG query text | Suppressed entirely | SHA-256 hash logged instead |

`PiiMasker.java` provides static utility methods used at any log-call site. The raw
values still flow through the prediction pipeline — masking is log-output only.

### 13.7 Data access audit log

Every request to a protected endpoint writes one row to `security_audit_log`:

```
action         = CREATE_PREDICTION | READ_PREDICTION | ADMIN_REPLAY
principal      = JWT "sub" claim   (e.g. "service-account-occasion-predictor-api")
                 or "anonymousUser" when security is disabled
resource_id    = orderId (for CREATE / REPLAY) or prediction UUID (for READ)
roles          = comma-separated ROLE_* values from the JWT
ip_address     = client IP (X-Forwarded-For first entry, or RemoteAddr)
occurred_at    = timestamp at time of request
```

The same information goes to the `SECURITY_AUDIT` SLF4J logger so it can be routed
to a dedicated file appender in `logback-spring.xml`:

```xml
<!-- logback-spring.xml (optional — add to src/main/resources) -->
<appender name="AUDIT_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
  <file>logs/security-audit.log</file>
  <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
    <fileNamePattern>logs/security-audit.%d{yyyy-MM-dd}.log</fileNamePattern>
    <maxHistory>90</maxHistory>
  </rollingPolicy>
  <encoder><pattern>%d{ISO8601} %msg%n</pattern></encoder>
</appender>

<logger name="SECURITY_AUDIT" level="INFO" additivity="false">
  <appender-ref ref="AUDIT_FILE"/>
</logger>
```

### 13.8 Keycloak setup (complete reference)

#### Start

```bash
docker compose up -d keycloak
# Admin console: http://localhost:8180   login: admin / admin
```

#### Realm

| Setting | Value |
|---|---|
| Realm name | `occasion-predictor` |

#### Client

| Setting | Value |
|---|---|
| Client ID | `occasion-predictor-api` |
| Client type | `OpenID Connect` |
| Client authentication | `ON` (confidential client) |
| Service accounts roles | `ON` (enables client-credentials flow) |
| Valid redirect URIs | `http://localhost:8081/*` (for auth-code flow later) |

After saving, go to the **Credentials** tab and copy the **Client secret**.

#### Realm roles (create all four)

| Role | Description |
|---|---|
| `ADMIN` | Full access including replay endpoint |
| `SERVICE` | Create + read predictions (M2M) |
| `REVIEWER` | Read predictions only |
| `READ_ONLY` | Health check only |

#### Service account role assignment

Clients → `occasion-predictor-api` → Service accounts roles tab → Assign role →
Filter by realm roles → select `SERVICE` → Assign

#### Get a token

```bash
# Client credentials (machine-to-machine)
curl -s -X POST \
  "http://localhost:8180/realms/occasion-predictor/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=occasion-predictor-api" \
  -d "client_secret=<YOUR_SECRET>"

# Password (test user — dev only)
curl -s -X POST \
  "http://localhost:8180/realms/occasion-predictor/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=occasion-predictor-api" \
  -d "client_secret=<YOUR_SECRET>" \
  -d "username=admin-user" \
  -d "password=<USER_PASSWORD>"
```

Response contains `access_token` (short-lived, ~5 min) and `refresh_token` (longer).

#### Token expiry and refresh

```bash
# Refresh an expired access token without re-authenticating
curl -s -X POST \
  "http://localhost:8180/realms/occasion-predictor/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token" \
  -d "client_id=occasion-predictor-api" \
  -d "client_secret=<YOUR_SECRET>" \
  -d "refresh_token=<REFRESH_TOKEN>"
```

Client-credentials tokens cannot be refreshed (no `refresh_token` issued) — just
re-fetch with `grant_type=client_credentials`.

#### Inspect a JWT without calling Keycloak

```bash
# Decode payload (no signature verification — for debugging only)
echo "<YOUR_TOKEN>" | cut -d. -f2 | base64 -d 2>/dev/null | python3 -m json.tool
```

#### Verify the JWKS endpoint (confirm Keycloak is reachable)

```bash
curl -s http://localhost:8180/realms/occasion-predictor/protocol/openid-connect/certs | python3 -m json.tool
# Should return a JSON object with "keys": [{...RSA key...}]
```

#### Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| App fails to start: `Connection refused :8180` | Keycloak not running | `docker compose up -d keycloak`, wait 30 s |
| `invalid_client` on token request | Wrong client secret | Clients → Credentials tab → re-copy |
| `401 Unauthorized` from API | Missing or malformed token | Add `Authorization: Bearer <token>` header |
| `401` with token present | Token expired | Re-fetch token; check clock skew |
| `403 Forbidden` | Token valid, role missing | Clients → Service accounts roles → re-assign |
| Roles not in JWT | Role assigned but not appearing | Inspect token with `cut -d. -f2 | base64 -d`; check realm vs. client role scope |
| `iss` mismatch error | JWT_ISSUER_URI wrong | Must exactly match `iss` in the token — check for trailing slash |

---

*Updated to Phase 10 completion — Phases 1–8 + Phase 10 complete. Phase 9 (rules engine) is a future enhancement.*
