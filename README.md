1# Occasion Predictor AI

A Spring Boot service that predicts gift occasions (Birthday, Christmas, Valentine's Day, etc.) from order and recipient data using a local LLM (Ollama) augmented by RAG (Retrieval-Augmented Generation) over a pgvector knowledge base.

Part of a larger **Gift Reminder AI** platform where predicted occasions drive automated gift reminders.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 24 |
| Framework | Spring Boot 3.5 |
| AI / LLM | Spring AI 1.0.3 + Ollama |
| Chat model | `llama3:latest` |
| Embedding model | `nomic-embed-text:latest` (768-dim) |
| Vector store | pgvector (HNSW index) |
| Cache | Redis 7 (Lettuce) |
| Database | PostgreSQL 18 |
| Migrations | Flyway |
| Docs | Springdoc OpenAPI / Swagger UI |
| Build | Maven |

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 24 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| PostgreSQL | 16+ | With **pgvector extension** installed |
| Redis | 7+ | `redis-server` or Docker |
| Ollama | Latest | [ollama.com](https://ollama.com) |

### Install pgvector

**Option A — Docker (recommended, zero setup):**
```bash
docker run --name occasion-predictor-db \
  -e POSTGRES_DB=occasion_predictor \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 -d pgvector/pgvector:pg17
```

Or with the included `docker-compose.yml`:
```bash
docker compose up -d
```

**Option B — EDB PostgreSQL installer (DMG on macOS):**
```bash
# Build pgvector from source against the EDB installation
cd /tmp && git clone https://github.com/pgvector/pgvector.git && cd pgvector
make PG_CONFIG=/Library/PostgreSQL/18/bin/pg_config
sudo make install PG_CONFIG=/Library/PostgreSQL/18/bin/pg_config
```

### Pull Ollama models
```bash
ollama pull llama3
ollama pull nomic-embed-text
```

---

## Run Instructions

```bash
# 1. Clone and enter the project
git clone <repo-url>
cd occasion-predictor-ai

# 2. Start PostgreSQL + Redis (if using Docker)
docker compose up -d

# 3. Start Ollama (in a separate terminal or as a background service)
ollama serve

# 4. Build and run
mvn spring-boot:run
```

On first startup the app will:
- Run the single Flyway migration (`V1__init.sql`), which enables the `vector` extension, creates all tables, and seeds the prompt and model
- Ingest the five RAG knowledge-base documents into pgvector via `nomic-embed-text` embeddings (only if the store is empty)

**If running Redis locally (no Docker):**
```bash
brew install redis && redis-server
```

**Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## API

### POST `/api/v1/occasion/predictions`

Predict the gift occasion for an order.

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

**Supported occasions:** `BIRTHDAY` · `ANNIVERSARY` · `VALENTINES_DAY` · `MOTHERS_DAY` · `FATHERS_DAY` · `CHRISTMAS` · `THANKSGIVING` · `UNKNOWN`

### GET `/api/v1/occasion/predictions/{id}`

Retrieve a stored prediction by UUID.

### GET `/api/v1/occasion/health`

Health check — returns `"Service is running!"`.

---

## End-to-End Flow

```
POST /api/v1/occasion/predictions
         │
         ▼
 OccasionController
         │ RateLimiterService.checkOrThrow(clientIp)  → 429 if exceeded
         ▼
 PredictionServiceImpl
         │
         ├─► 0. Redis idempotency check
         │       prediction:{orderId} hit? → return cached response instantly
         │
         ├─► 1. RagRetriever
         │       │  Build semantic query from request fields
         │       │  rag:{queryHash} hit? → return cached chunks (skip embed + search)
         │       │  Cache miss → embed query via nomic-embed-text (Ollama)
         │       └─► pgvector HNSW search → top-5 chunks → cache in Redis
         │
         ├─► 2. RagPromptBuilder
         │       │  System instructions  (from DB prompt_versions)
         │       │  + Retrieved context  (top-k chunks with section labels)
         │       └─► + Order details     (recipient, product, date, message)
         │
         ├─► 3. PredictionWorkflow
         │       │  ChatClient.prompt(ragPrompt).call()
         │       │  → Ollama llama3
         │       │  ← JSON response
         │       │  Validate: enum check, confidence range [0,1]
         │       └─► Confidence < 0.4 → force UNKNOWN
         │
         └─► 4. Persist + cache result
                 predictions        (occasion, confidence, reason, evidence)
                 prediction_audit   (raw prompt, raw response, model params,
                                     rag_chunk_ids, latency_ms)
                 Redis prediction:{orderId} → cached for 24 h
```

---

## RAG Knowledge Base

Five documents are loaded, chunked (800-char chunks, 100-char overlap), embedded, and stored in pgvector on first startup:

| File | Content |
|---|---|
| `occasion_rules.md` | Keyword and date-window signals per occasion |
| `gift_category_rules.md` | Product category → occasion confidence boosts |
| `recipient_relation_rules.md` | Recipient relation → primary/secondary occasion |
| `brand_rules.md` | Brand → category and peak-occasion mapping |
| `holiday_calendar.json` | Fixed and floating holiday dates with lead times (US/UK) |

Each chunk carries metadata: `docName`, `category`, `section`, `country`, `brand`, `ruleId`.

---

## Database Schema

```
prompt_versions      — versioned LLM system instruction templates
model_versions       — tracked Ollama model names and providers
predictions          — one row per prediction (occasion, confidence, reason, evidence)
prediction_audit     — raw prompt, raw LLM response, model parameters, rag_chunk_ids, latency
vector_store         — pgvector table (768-dim embeddings + JSON metadata per chunk)
```

A single Flyway migration (`V1__init.sql`) creates the full schema and seeds all reference data.

---

## Configuration

Key properties in `application.yml`:

```yaml
spring:
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
    top-k: 5                    # chunks retrieved per prediction
    similarity-threshold: 0.5   # minimum cosine similarity to include a chunk
  cache:
    prediction-ttl-seconds: 86400  # idempotency window per orderId (24 h)
    rag-ttl-seconds: 1800          # RAG retrieval result cache (30 min)
  rate-limit:
    requests-per-minute: 20        # per client IP, fixed window
```

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
| 8 | _(planned)_ Feedback loop + confidence calibration | 🔲 |
| 9 | _(planned)_ Multi-country holiday calendar expansion | 🔲 |
