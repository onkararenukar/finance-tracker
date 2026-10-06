# Finance Tracker

A multi-bank personal finance tracker: upload bank statements (PDF/CSV),
extract and categorize transactions via an LLM, and view an interactive
income/expense dashboard — including a live view of Kafka event traffic
moving through the system.

## Architecture

```
   sms-sync-agent (runs on your Mac, NOT Docker)
        │ uploads via HTTP
        ▼
                     ┌──────────────────┐
   client ─────────► │   API Gateway     │  (BUILT)
                     └────────┬──────────┘
                              │
        ┌─────────────────────┼───────────────────────┐
        ▼                     ▼                        ▼
┌───────────────┐   ┌───────────────────┐    ┌───────────────────┐
│  Ingestion     │──►│  Parsing           │──►│  Categorization    │
│  Service       │Kafka  Service        │Kafka  Service          │
│  (BUILT)       │   │  (BUILT)           │    │  (BUILT)           │
└───────┬────────┘   └─────────┬──────────┘    └──────────┬─────────┘
        │                      │                            │
        ▼                      ▼                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              PostgreSQL + pgvector (single database)              │
└─────────────────────────────────────────────────────────────────┘
                              ▲
                              │
                    ┌───────────────────┐
                    │  Analytics /       │
                    │  Dashboard Service │
                    │  (not yet built)   │
                    └───────────────────┘

   API Gateway also hosts ws://.../ws/kafka-events, relaying live
   Kafka traffic (topic/partition/size/timestamp) to the dashboard's
   live data-flow visualization page.
```

Every service publishes/consumes events through Kafka rather than calling
each other directly over HTTP — this decouples them (the parsing-service
doesn't need to know ingestion-service exists, just that
`statement.ingested` events show up) and gives us the event stream needed
for the "live Kafka data-transfer visuals" dashboard page for free.

## What's built so far: `ingestion-service`

Accepts a bank statement upload (PDF or CSV, any bank), extracts its raw
text into bounded chunks, stores a tracking row in Postgres, and
publishes a `statement.ingested` Kafka event for the next service in the
pipeline to consume.

| Concern | How it's handled |
|---|---|
| Duplicate uploads | Rejected by SHA-256 checksum, both in-app and via a unique DB index |
| Schema management | Flyway migration (`V1__create_bank_statements_table.sql`) — never `ddl-auto: update` |
| Vector-ready | `pgvector` extension enabled on the shared Postgres instance from day one, ready for the parsing-service's embeddings table |
| Concurrency | Virtual threads (JEP, finalized) for request handling — I/O-bound upload/extraction work doesn't block a platform thread |
| Chunking | `java.util.stream.Gatherers.windowFixed` (JEP 485, new in Java 24) splits extracted text into fixed-size windows |
| Type dispatch | Pattern matching for `switch` (finalized in Java 24) dispatches PDF vs CSV extraction with compiler-checked exhaustiveness |
| Failure visibility | Every pipeline step updates the row's `status`; failures are recorded with a reason instead of only appearing in logs |

## What's built so far: `parsing-service`

Consumes `statement.ingested`, calls an LLM to turn each raw chunk into
structured transactions, persists them, computes + stores a pgvector
embedding per transaction, and publishes `transaction.parsed`.

| Concern | How it's handled |
|---|---|
| Concurrent chunk parsing | `StructuredTaskScope.ShutdownOnFailure` (JEP 499, preview) fans out one LLM call per chunk, joins, and auto-cancels the rest if one fails |
| Local vs. hosted LLM | Pure config toggle - `finance-tracker.llm.chat.base-url` / `.embedding.base-url`, both OpenAI-compatible endpoints |
| Vector storage | `com.pgvector:pgvector` + plain JDBC (deliberately bypassing JPA for the `vector` column - see `TransactionEmbeddingStore` javadoc) with an HNSW cosine-distance index |
| Poison messages | Manual Kafka ack + a dedicated `statement.ingested.DLT` dead-letter topic, so one bad statement never blocks the consumer group |
| Cross-service event contracts | `StatementIngestedEvent` is deliberately re-declared (not shared via a common JAR) in this service's own package - see its javadoc for the coupling trade-off |
| Concurrency for I/O | Kafka listener container runs on a `VirtualThreadTaskExecutor`, so many statements can be "in flight" awaiting LLM responses at once |

## What's built so far: `categorization-service`

Consumes `transaction.parsed`, asks the LLM to categorize against your
existing category list, auto-confirms matches, and flags anything new
for your review instead of silently creating it.

| Concern | How it's handled |
|---|---|
| New-category detection | LLM response checked case-insensitively against `categories`; no match -> `PENDING_REVIEW`, category_id left NULL |
| Your review workflow | `GET /api/v1/transaction-categories/pending` (paginated) returns each item with a `highlightColor` field (`#FF4F79`, pinkish-red) baked in - no separate lookup needed on the frontend |
| Resolving a pending item | `POST /api/v1/transaction-categories/{id}/assign` with either an existing `categoryId` or a `newCategoryName` you're confirming - a new `categories` row is created ONLY here, never automatically |
| Category list for pickers | `GET /api/v1/categories` |
| Seed data | 14 starter categories (Groceries, Food & Dining, Transport, ...) inserted by Flyway - add more anytime with a normal INSERT or via the assign endpoint |

## What's built so far: `sms-sync-agent`

Runs **directly on your Mac** (not Docker - see its own `README.md` for
why) and feeds your bank's SMS/iMessage alerts into the same pipeline
statement uploads use, with **no changes needed anywhere else** - it
reuses ingestion-service's existing upload endpoint by formatting new
bank messages as a synthetic CSV.

| Concern | How it's handled |
|---|---|
| Real-time detection | `fswatch` (native FSEvents) watches `chat.db` for changes and triggers a sync within seconds of a message arriving - not polling |
| Fallback | If `fswatch` isn't installed, falls back to a fixed-interval poll with a logged suggestion to install it |
| Bank message filtering | Requires both transaction-language AND a recognizable amount before treating a message as a bank alert - tuned for high precision |
| Full Disk Access | Documented explicitly in `sms-sync-agent/README.md` - this is the #1 thing that silently breaks this agent if skipped |
| Duplicate-safe retries | Reuses ingestion-service's checksum-based dedup - a retried identical batch is safely rejected as a 409, not double-processed |

## What's built so far: `api-gateway`

The single public entrypoint (port 8080) - everything else stays
internal to the Docker network. Runs on Netty/WebFlux (Spring Cloud
Gateway), unlike every other service here, which is Tomcat/Servlet-based.

| Concern | How it's handled |
|---|---|
| Routing | `/api/v1/statements/**` → ingestion-service, `/api/v1/categories/**` and `/api/v1/transaction-categories/**` → categorization-service. No route for parsing-service - it has no public API. |
| Live Kafka visuals | `ws://localhost:8080/ws/kafka-events` streams JSON metadata (topic, partition, offset, size, timestamps) for every message on `statement.ingested`, `transaction.parsed`, and both DLT topics, in real time |
| Privacy on the live feed | Only metadata is relayed, never message payloads (transaction descriptions/amounts) - see `KafkaLiveEvent`'s javadoc |
| Isolation from real processing | The live-view Kafka consumer uses its own dedicated consumer group (`api-gateway-live-view`), so watching traffic never interferes with parsing-service's or categorization-service's actual message processing or consumer lag |
| CORS | Configured for a dashboard dev server on a different origin (`DASHBOARD_ORIGIN`, defaults to `http://localhost:5173`) |

Try the live feed once everything's running:
```bash
# npm install -g wscat, if you don't have it
wscat -c ws://localhost:8080/ws/kafka-events
# then upload a statement in another terminal and watch events stream in
```

## Running it all locally

```bash
cd finance-tracker
docker compose up --build   # postgres+pgvector, kafka, kafka-ui, gateway + all 3 backend services

# Optional: feed it from your Mac's Messages automatically
brew install fswatch
mvn -pl sms-sync-agent -am package
java -jar sms-sync-agent/target/sms-sync-agent.jar   # see sms-sync-agent/README.md for one-time setup

# Everything now goes through the gateway on 8080, not individual service ports
curl -X POST http://localhost:8080/api/v1/statements \
  -F "file=@/path/to/statement.pdf" -F "bankName=HDFC Bank"

curl http://localhost:8080/api/v1/transaction-categories/pending

# Resolve one - either an existing category...
curl -X POST http://localhost:8080/api/v1/transaction-categories/101/assign \
  -H "Content-Type: application/json" -d '{"categoryId": 5}'
# ...or confirm it as genuinely new
curl -X POST http://localhost:8080/api/v1/transaction-categories/101/assign \
  -H "Content-Type: application/json" -d '{"newCategoryName": "Pet Care"}'
```

## What's next

**analytics-service** + the React dashboard - the last piece. Income/
expense charts, category breakdowns, the pending-review queue rendered
with the pinkish-red highlighting (the data's already there, from
categorization-service), and a live-updating visualization consuming
the `ws://.../ws/kafka-events` feed built above.

Say the word and I'll build it.
