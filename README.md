# Occasion Predictor AI

A Spring Boot service that predicts gift occasions (Birthday, Christmas, Valentine's Day, etc.) from order and recipient data using a local LLM (Ollama) augmented by RAG (Retrieval-Augmented Generation) over a pgvector knowledge base.

Part of a larger **Gift Reminder AI** platform where predicted occasions drive automated gift reminders via an event-driven pipeline.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 24 |
| Framework | Spring Boot 3.5 |
| AI / LLM | Spring AI 1.0.3 + Ollama |
| Chat model | `llama3:latest` |
| Embedding model | `nomic-embed-text:latest` (768-dim) |
| Vector store | pgvector (HNSW index, cosine distance) |
| Cache | Redis 7 (Lettuce — idempotency + RAG retrieval cache) |
| Message bus | Apache Kafka 3.7 (KRaft, no ZooKeeper) |
| Database | PostgreSQL 18 |
| Migrations | Flyway (single `V1__init.sql`) |
| Docs | Springdoc OpenAPI / Swagger UI |
| Security | Spring Security 6 + OAuth2 Resource Server (JWT) |
| Auth provider | Keycloak 24 (optional — disabled by default for local dev) |
| Build | Maven |

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 24 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| PostgreSQL | 16+ | With **pgvector** extension installed |
| Redis | 7+ | `redis-server` or Docker |
| Kafka | 3.7+ (KRaft) | `kafka-server-start` or Docker |
| Ollama | Latest | Pull `llama3` + `nomic-embed-text` |
| Keycloak | 24+ _(optional)_ | Docker recommended; skip for local dev (security disabled by default) |

---

## Setup

### PostgreSQL + pgvector

**Option A — Docker (recommended):**
```bash
docker compose up -d postgres
```

**Option B — EDB PostgreSQL DMG on macOS (build pgvector from source):**
```bash
cd /tmp && git clone https://github.com/pgvector/pgvector.git && cd pgvector
make PG_CONFIG=/Library/PostgreSQL/18/bin/pg_config
sudo make install PG_CONFIG=/Library/PostgreSQL/18/bin/pg_config
```

Then create the database (only needed once):
```bash
/Library/PostgreSQL/18/bin/psql -U postgres \
  -c "DROP DATABASE IF EXISTS occasion_predictor; CREATE DATABASE occasion_predictor;"
```

### Redis

```bash
# Docker
docker compose up -d redis

# Homebrew
brew install redis && redis-server
```

### Kafka (KRaft — no ZooKeeper)

```bash
# Docker
docker compose up -d kafka

# Homebrew
brew install kafka
# First time only — format the storage:
kafka-storage format -t $(kafka-storage random-uuid) \
  -c /opt/homebrew/etc/kafka/server.properties
# Start:
kafka-server-start /opt/homebrew/etc/kafka/server.properties
```

Topics are created automatically on first app startup via `KafkaTopicConfig`.

### Ollama models

```bash
ollama pull llama3
ollama pull nomic-embed-text
```

### Keycloak (optional — only needed when `app.security.enabled=true`)

Security is **off by default**. Skip this entire section for local development — all endpoints are open without a token.

#### 1. Start Keycloak

```bash
docker compose up -d keycloak
```

Wait ~30 seconds, then open **http://localhost:8180** and log in with `admin` / `admin`.

#### 2. Create the realm

1. Click the realm dropdown (top-left, shows "Keycloak") → **Create realm**
2. Name: `occasion-predictor`
3. Click **Create**

All following steps happen inside this realm.

#### 3. Create the API client

This client represents the Spring Boot app (or any service calling it).

1. Left menu → **Clients** → **Create client**
2. Fill in:
   - Client type: `OpenID Connect`
   - Client ID: `occasion-predictor-api`
3. Click **Next**
4. On the **Capability config** page, toggle **ON**:
   - `Client authentication` (makes this a confidential client — gives you a secret)
   - `Service accounts roles` (enables client-credentials / machine-to-machine flow)
5. Click **Next** → **Save**

#### 4. Copy the client secret

1. Clients → `occasion-predictor-api` → **Credentials** tab
2. Copy the value under **Client secret** — you will need it to get tokens

#### 5. Create realm roles

1. Left menu → **Realm roles** → **Create role** — repeat for each:

| Role name | Who uses it |
|---|---|
| `ADMIN` | Full access — ops team, admin dashboards |
| `SERVICE` | Other backend services calling this API (machine-to-machine) |
| `REVIEWER` | Human users who read predictions (read-only) |
| `READ_ONLY` | Health-check monitoring only |

#### 6. Assign the SERVICE role to the client's service account

The service account is the identity used in client-credentials flow.

1. Clients → `occasion-predictor-api` → **Service accounts roles** tab
2. Click **Assign role**
3. Switch the filter to **Filter by realm roles**
4. Select `SERVICE` → **Assign**

The client can now call `POST /predictions` and `GET /predictions/{id}`.

#### 7. (Optional) Create a human test user with ADMIN role

1. Left menu → **Users** → **Create new user**
2. Username: `admin-user`, Email verified: On → **Create**
3. **Credentials** tab → **Set password** → enter a password, Temporary: Off
4. **Role mappings** tab → **Assign role** → pick `ADMIN`

#### 8. Start the app with security active

```bash
export SECURITY_ENABLED=true
export JWT_ISSUER_URI=http://localhost:8180/realms/occasion-predictor
mvn spring-boot:run
```

The app logs: `[SECURITY] OAuth2 JWT resource server active. Issuer: http://localhost:8180/realms/occasion-predictor`

#### 9. Get a token and call the API

**Machine-to-machine (client credentials — most common):**

```bash
# Get a token (replace YOUR_SECRET with the value from step 4)
TOKEN=$(curl -s -X POST \
  "http://localhost:8180/realms/occasion-predictor/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=occasion-predictor-api" \
  -d "client_secret=YOUR_SECRET" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# Call the API
curl -X POST http://localhost:8081/api/v1/occasion/predictions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-001",
    "recipientName": "Sarah",
    "recipientRelation": "Mother",
    "productName": "Luxury Rose Bouquet",
    "productCategory": "Flowers",
    "orderDate": "2025-05-08",
    "giftMessage": "Happy Mothers Day!"
  }'
```

**Human user (password flow — for ADMIN user created in step 7):**

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8180/realms/occasion-predictor/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=occasion-predictor-api" \
  -d "client_secret=YOUR_SECRET" \
  -d "username=admin-user" \
  -d "password=YOUR_PASSWORD" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# Replay an event (ADMIN only)
curl -X POST http://localhost:8081/api/v1/occasion/admin/replay/ORD-001 \
  -H "Authorization: Bearer $TOKEN"
```

#### 10. What happens without a token (when security is on)

```bash
curl -X POST http://localhost:8081/api/v1/occasion/predictions \
  -H "Content-Type: application/json" -d '{...}'
# → 401 Unauthorized

curl -X POST http://localhost:8081/api/v1/occasion/admin/replay/ORD-001 \
  -H "Authorization: Bearer $SERVICE_TOKEN"   # SERVICE role, not ADMIN
# → 403 Forbidden
```

#### Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| App won't start — `Connection refused localhost:8180` | Keycloak not running | `docker compose up -d keycloak` and wait 30 s |
| `401 Unauthorized` | Missing or expired token | Re-fetch token; check `SECURITY_ENABLED=true` |
| `403 Forbidden` | Token valid but wrong role | Check service-account role assignment in Keycloak |
| `invalid_client` on token request | Wrong client secret | Re-copy secret from Clients → Credentials tab |
| Roles not showing in JWT | Role not assigned to service account | Clients → Service accounts roles → re-assign |

For local development without Keycloak, leave `app.security.enabled=false` (the default) — all endpoints remain open.

---

## Run Instructions

```bash
# 1. Clone and enter the project
git clone <repo-url>
cd occasion-predictor-ai

# 2. Start infrastructure (all three, if using Docker)
docker compose up -d

# 3. Start Ollama
ollama serve

# 4. Run the app
mvn spring-boot:run
```

On first startup the app will:
- Run `V1__init.sql` — enables the `vector` extension, creates all tables, seeds the prompt template and model
- Ingest the five RAG knowledge-base documents into pgvector (skipped if already populated)
- Auto-create Kafka topics: `prediction.requested`, `occasion.predicted`, `prediction.failed`, `prediction.retry`, `prediction.dlq`

**Swagger UI:** [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)

---

## API

### `POST /api/v1/occasion/predictions`

Predict the gift occasion for an order. Rate-limited to **20 requests per minute per IP** (returns `429` if exceeded).

**Request:**
```json
{
  "orderId": "ORD-001",
  "recipientName": "Sarah",
  "recipientRelation": "Mother",
  "productName": "Luxury Rose Bouquet",
  "productCategory": "Flowers",
  "orderDate": "2025-05-08",
  "giftMessage": "Happy Mother's Day Mum! Love you always."
}
```

**Response:**
```json
{
  "orderId": "ORD-001",
  "predictedOccasion": "MOTHERS_DAY",
  "confidenceScore": 0.95,
  "reason": "The recipient is the sender's mother and the order date falls within Mother's Day gifting window.",
  "predictionSource": "OLLAMA_llama3",
  "evidence": [
    "recipient relation is Mother",
    "gift message contains 'Mother's Day'",
    "order date is early May"
  ]
}
```

Duplicate `orderId` requests are served instantly from Redis (no LLM call) for 24 hours.

**Supported occasions:** `BIRTHDAY` · `ANNIVERSARY` · `VALENTINES_DAY` · `MOTHERS_DAY` · `FATHERS_DAY` · `CHRISTMAS` · `THANKSGIVING` · `UNKNOWN`

### `GET /api/v1/occasion/predictions/{id}`

Retrieve a stored prediction by its UUID.

### `POST /api/v1/occasion/admin/replay/{orderId}`

Re-publishes `OccasionPredictedEvent` for the most recent stored prediction of an `orderId`. All downstream consumers (reminder, notification, analytics, feedback) reprocess the event without invoking the LLM again. Returns `202 Accepted`.

### `GET /api/v1/occasion/health`

Returns `"Service is running!"`.

---

## End-to-End Flow

```
POST /api/v1/occasion/predictions
         │
         ▼
 OccasionController
         │ RateLimiterService → Redis fixed-window counter (20 req/min/IP)
         │                      → 429 Too Many Requests if exceeded
         ▼
 PredictionServiceImpl
         │
         ├─► 0. Publish prediction.requested  ──────────────────► Kafka
         │
         ├─► 1. Redis idempotency check
         │       prediction:{orderId} hit? → return cached response instantly
         │
         ├─► 2. RagRetriever
         │       │  Build semantic query from request fields
         │       │  rag:{sha256(query)} hit? → return cached chunks (skip embed + search)
         │       │  Cache miss:
         │       │    embed query → nomic-embed-text (Ollama)
         │       │    pgvector HNSW cosine search → top-5 chunks
         │       └─► cache chunks in Redis (TTL 30 min)
         │
         ├─► 3. RagPromptBuilder
         │       │  System instructions  (from DB prompt_versions)
         │       │  + Retrieved context  (top-k chunks with section labels)
         │       └─► + Order details     (recipient, product, date, message)
         │
         ├─► 4. PredictionWorkflow (Ollama)
         │       │  ChatClient.prompt(ragPrompt).call() → llama3
         │       │  Parse JSON response
         │       │  Validate: enum check · confidence [0,1]
         │       └─► Confidence < 0.4 → force UNKNOWN
         │
         ├─► 5. Persist
         │       predictions      (occasion, confidence, reason, evidence)
         │       prediction_audit (raw prompt, raw LLM response, model params,
         │                         rag_chunk_ids, latency_ms)
         │
         ├─► 6. Cache prediction:{orderId} in Redis (TTL 24 h)
         │
         └─► 7. Publish occasion.predicted  ───────────────────► Kafka
                    │
                    ├── ReminderSchedulingConsumer  (reminder-scheduling-group)
                    ├── NotificationConsumer        (notification-group)
                    ├── AnalyticsConsumer           (analytics-group)
                    └── FeedbackConsumer            (feedback-group)

On LLM exception:
         └─► Publish prediction.failed ─────────────────────────► Kafka
                    │
                    └── RetryConsumer  (prediction-retry-group)
                            │  Exponential backoff retry (up to 3 attempts)
                            │  Each retry re-runs full predict() flow
                            │  Intermediate failures → prediction.retry
                            └─► Exhausted → prediction.dlq
                                    │
                                    └── DlqConsumer (logs + alerts)
```

---

## Kafka Event Bus

### Topics

| Topic | Partitions | Published when |
|---|---|---|
| `prediction.requested` | 1 | Prediction workflow starts (audit trail) |
| `occasion.predicted` | 3 | Prediction saved successfully |
| `prediction.failed` | 1 | LLM workflow throws an exception |
| `prediction.retry` | 1 | Retry hop between attempts |
| `prediction.dlq` | 1 | All retry attempts exhausted |

### Consumers

| Consumer | Group ID | Source | Action |
|---|---|---|---|
| `ReminderSchedulingConsumer` | `reminder-scheduling-group` | `occasion.predicted` | Schedule gift reminder |
| `NotificationConsumer` | `notification-group` | `occasion.predicted` | Send customer notification |
| `AnalyticsConsumer` | `analytics-group` | `occasion.predicted` | Record prediction metrics |
| `FeedbackConsumer` | `feedback-group` | `occasion.predicted` | Queue feedback collection |
| `RetryConsumer` | `prediction-retry-group` | `prediction.failed` + `prediction.retry` | Retry LLM call with backoff |
| `DlqConsumer` | `prediction-dlq-group` | `prediction.dlq` | Log + alert on permanent failures |

### Error Handling

Consumer-side processing failures (e.g. downstream service unavailable) are retried **twice** with a 2 s fixed backoff by the shared `DefaultErrorHandler`. After exhaustion the raw message is routed to `prediction.dlq`.

Service-level prediction failures (LLM unreachable) use the `RetryConsumer` with **exponential backoff** (2 s × 2^attempt, capped at 30 s), up to `app.kafka.retry.max-attempts` (default 3).

### Replay

```bash
curl -X POST http://localhost:8081/api/v1/occasion/admin/replay/ORD-001
# → 202 Accepted
# All downstream consumers reprocess without re-invoking the LLM
```

---

## RAG Knowledge Base

Five documents loaded, chunked (800-char chunks / 100-char overlap), embedded, and stored in pgvector on first startup:

| File | Content |
|---|---|
| `occasion_rules.md` | Keyword and date-window signals per occasion |
| `gift_category_rules.md` | Product category → occasion confidence boosts |
| `recipient_relation_rules.md` | Recipient relation → primary/secondary occasion mapping |
| `brand_rules.md` | Brand → category and peak-occasion mapping |
| `holiday_calendar.json` | Fixed and floating holiday dates with lead times (US/UK) |

Each chunk carries metadata: `docName`, `category`, `section`, `country`, `brand`, `ruleId`.

---

## Database Schema

```
prompt_versions      — versioned LLM system instruction templates
model_versions       — tracked Ollama model names and providers
predictions          — one row per prediction (occasion, confidence, reason, evidence)
prediction_audit     — raw prompt, raw LLM response, model parameters, rag_chunk_ids, latency_ms
vector_store         — pgvector table (768-dim HNSW embeddings + JSON metadata per chunk)
security_audit_log   — data-access and admin action audit trail (principal, action, resource, ip)
```

Two Flyway migrations: `V1__init.sql` (full app schema + seed data) and `V2__add_security_audit.sql` (security audit table).

---

## Configuration

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/occasion_predictor
    username: postgres
    password: postgres

  redis:
    host: localhost
    port: 6379

  kafka:
    bootstrap-servers: localhost:9092   # override with KAFKA_BOOTSTRAP_SERVERS env var
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false

  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: llama3:latest
        options:
          temperature: 0.1      # low = deterministic predictions
          num-predict: 200
      embedding:
        model: nomic-embed-text:latest
    vectorstore:
      pgvector:
        dimensions: 768
        index-type: hnsw
        distance-type: cosine_distance

app:
  rag:
    chunk-size: 800
    chunk-overlap: 100
    top-k: 5                         # chunks retrieved per prediction
    similarity-threshold: 0.5        # minimum cosine similarity to include a chunk
  cache:
    prediction-ttl-seconds: 86400    # idempotency window per orderId (24 h)
    rag-ttl-seconds: 1800            # RAG retrieval result cache (30 min)
  rate-limit:
    requests-per-minute: 20          # per client IP, fixed window
  kafka:
    retry:
      max-attempts: 3                # prediction retries before DLQ
      backoff-ms: 2000               # base backoff (doubles each attempt)
  security:
    enabled: false                   # true + JWT_ISSUER_URI env var = full OAuth2/JWT enforcement

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI:}   # e.g. http://localhost:8180/realms/occasion-predictor
```

---

## Redis Cache Keys

| Key pattern | TTL | Contents |
|---|---|---|
| `prediction:{orderId}` | 24 h | `PredictionResponse` JSON — idempotency |
| `rag:{sha256(query)}` | 30 min | `List<CachedChunk>` JSON — skip re-embed + vector search |
| `rate:{ip}:{minuteWindow}` | 60 s | Request counter — fixed-window rate limiter |

---

## Security (Phase 10)

### Roles

| Role | Allowed endpoints |
|---|---|
| `ADMIN` | All endpoints including `/admin/replay/{orderId}` |
| `SERVICE` | `POST /predictions`, `GET /predictions/{id}` (machine-to-machine) |
| `REVIEWER` | `GET /predictions/{id}` (read-only human access) |
| `READ_ONLY` | `GET /health` only |

### Endpoint access matrix

| Endpoint | ADMIN | SERVICE | REVIEWER | READ_ONLY | Anonymous |
|---|---|---|---|---|---|
| `GET /health` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST /predictions` | ✅ | ✅ | — | — | dev only |
| `GET /predictions/{id}` | ✅ | ✅ | ✅ | — | dev only |
| `POST /admin/replay/{orderId}` | ✅ | — | — | — | dev only |

"dev only" = permitted when `app.security.enabled=false` (local default).

### JWT claim format

Roles are read from the JWT using two strategies, tried in order:
1. **Keycloak** — `realm_access.roles: ["ADMIN", "SERVICE"]`
2. **Generic OIDC** — `roles: ["ADMIN", "SERVICE"]`

Both lists are merged and prefixed with `ROLE_` before Spring Security evaluates access.

### PII masking in logs

| Field | Log output |
|---|---|
| `recipientName` | `Sarah` → `S***h` |
| `giftMessage` | → `[REDACTED:42chars]` |
| RAG query | Raw text suppressed; only its SHA-256 hash is logged |

### Data access audit

Every `POST /predictions`, `GET /predictions/{id}`, and `POST /admin/replay/{orderId}` writes
a row to `security_audit_log` containing the JWT subject (`sub`), action name, resource ID,
granted roles, and client IP. Lines also go to the `SECURITY_AUDIT` SLF4J logger, which can
be routed to a dedicated audit appender in `logback-spring.xml`.

---

## Future Enhancements

### Phase 9 — Rules Engine _(planned)_

A deterministic pre-classification layer that runs before the LLM call. High-confidence
rules (e.g. a gift message containing "Happy Mother's Day" with a date in May) would
short-circuit to a result without consuming LLM tokens, improving throughput and reducing
cost for straightforward cases. Planned as a future enhancement.

---

## Project Phases

| Phase | Description | Status |
|---|---|---|
| 1 | REST API + Spring AI + Ollama wiring | ✅ |
| 2 | PostgreSQL + JPA entities + Flyway migrations | ✅ |
| 3 | Spring AI `ChatClient` flow replacing manual RestTemplate | ✅ |
| 4 | Structured JSON output + hallucination controls + full audit trail | ✅ |
| 5 | RAG foundation — document ingestion → chunking → pgvector | ✅ |
| 6 | RAG retrieval in prediction flow — top-k context → RAG prompt | ✅ |
| 7 | Redis — idempotency cache, RAG retrieval cache, rate limiting | ✅ |
| 8 | Kafka — event-driven pipeline, retry/DLQ, replay endpoint | ✅ |
| 9 | Rules engine — deterministic pre-classification layer _(future enhancement)_ | 🔲 |
| 10 | Spring Security — OAuth2/JWT, role-based access, PII masking, data access audit | ✅ |
