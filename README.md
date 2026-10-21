# rag-java-spring-claude

A Retrieval-Augmented Generation (RAG) lab in Java 21 and Spring Boot. It answers questions about bank card procedures using only the loaded documents, and abstains instead of guessing when the answer is not there.

Built step by step with a measure-first approach: a 30-question evaluation set, a held-out split, and an LLM judge drove every change. Retrieval went from 5/10 to 29/30 and generation reached 30/30 correct answer-or-abstain decisions with zero hallucinations. The full history, decisions and lessons are in [EVALUATION.md](EVALUATION.md).

> The source documents, prompts and evaluation questions are in Spanish, because the domain (bank customer support) is Spanish-speaking. Code and docs are in English.

## Highlights

- **Two-stage retrieval**: query rewriting (multi-query), vector search in pgvector with a loose threshold, then an LLM reranker that keeps the best fragments or answers `NINGUNO` (none).
- **Grounded generation** with explicit abstention when the context does not contain the answer.
- **Pluggable LLM** behind a `LlmClient` port: local Ollama (qwen2.5:3b) or Claude through the official Anthropic Java SDK, switched by one property.
- **Evaluation suite**: retrieval recall, answer/abstain decision, format checks, latency, token usage and real cost per question, plus an LLM-as-judge scoring faithfulness, completeness and relevance.
- **Production basics**: secrets in environment variables, prompts in files (cached), timeouts and retries, idempotent ingestion that also removes deleted documents, full cost attribution across all LLM calls, and SSE streaming on virtual threads.

## Architecture

```
                 INGESTION (POST /api/documents/ingest)
documents/*.txt ─► DocumentLoader ─► DocumentChunker ─► embeddings (bge-m3) ─► PostgreSQL + pgvector

                 QUERY (POST /api/rag/ask, GET /api/rag/ask/stream)
question ─► QueryRewriter ─► vector search (original + rewritten question)
         ─► LlmReranker (filters candidates) ─► grounded prompt ─► LLM ─► answer + sources + metrics
```

| Stage | Class | What it does |
|---|---|---|
| Loading | `ingestion/DocumentLoader` | Reads the `.txt` files and normalizes line endings (`\r\n` → `\n`). |
| Chunking | `ingestion/DocumentChunker` | Splits on sentence boundaries into ~500-character chunks. Supports sentence-based overlap (currently 0). |
| Ingestion | `ingestion/DocumentIngestionService` | Computes embeddings outside the transaction, replaces each document as a whole (re-ingesting never duplicates) and deletes documents that are no longer in the folder. |
| Search | `service/DocumentChunkService` | Cosine distance in pgvector with a threshold and a limit. |
| Rewriting | `service/QueryRewriter` | Rephrases the question with the documents' vocabulary; validates the output (first line, max 300 characters). |
| Reranking | `service/LlmReranker` | The LLM picks which candidates answer the question, or `NINGUNO`. |
| Orchestration | `service/RetrievalService`, `service/RagService` | Two-stage retrieval, context building, the LLM call and metrics for every call. |
| Streaming | `service/RagStreamingService` | Sends the answer over SSE on virtual threads; the controller only delegates. |
| LLM | `llm/LlmClient` | Port with two adapters: `OllamaLlmClient` and `ClaudeLlmClient`. |
| Prompts | `prompt/PromptTemplateService` | Loads prompts from `resources/prompts` and caches them. |

## Requirements

- Java 21 (the Maven Wrapper is included).
- PostgreSQL with the `pgvector` extension (Docker Compose provided).
- [Ollama](https://ollama.com) on `http://localhost:11434`:
  ```bash
  ollama pull bge-m3        # embeddings (always required)
  ollama pull qwen2.5:3b    # local LLM (only when rag.llm.provider=ollama)
  ```
- To use Claude: an Anthropic API key with credit.

## Configuration

Secrets live in **environment variables**, never in code, `application.properties` or git:

| Variable | Used for |
|---|---|
| `DB_PASSWORD` | PostgreSQL password (also used by Docker Compose). |
| `ANTHROPIC_API_KEY` | Anthropic API key (only with `rag.llm.provider=claude`). |

On Windows (PowerShell), once, then restart the IDE or terminal:

```powershell
setx DB_PASSWORD "your_password"
setx ANTHROPIC_API_KEY "your_api_key"
```

Main properties in `src/main/resources/application.properties`:

| Property | Value | Purpose |
|---|---|---|
| `rag.llm.provider` | `claude` | `claude` or `ollama`: which LLM generates, rewrites and reranks. |
| `anthropic.model` / `anthropic.judge-model` | `claude-haiku-5-5` | Generator model and judge model. |
| `anthropic.timeout` / `anthropic.max-retries` | `30s` / `3` | Resilience against a slow or failing API. |
| `rag.chunk-size` / `rag.chunk-overlap` | `500` / `0` | Chunk size and overlap (see EVALUATION.md). |
| `rag.retrieval.threshold` / `rag.retrieval.limit` | `0.40` / `8` | Single-stage retrieval (when the reranker is off). |
| `rag.query-rewriting.enabled` | `true` | Turns query rewriting on. |
| `rag.rerank.enabled` | `true` | Turns two-stage retrieval on. |
| `rag.rerank.candidate-threshold` / `candidates` / `top-n` | `0.45` / `15` / `5` | Wide first stage; the second stage keeps at most 5. |
| `rag.streaming.timeout-ms` | `120000` (default) | Maximum time for a streamed answer. |
| `logging.level.com.albaro.rag.demo.service.*` | `INFO` / `DEBUG` | `DEBUG` logs the context, the rewritten query and the reranker's choice. |

Prompts live in `src/main/resources/prompts/`. They must use **LF** line endings (enforced by `.gitattributes`): with CRLF the model's behaviour changed measurably.

- `rag-system.txt`, `rag-user.txt`: answer generation (`{context}`, `{question}`).
- `query-rewriter-system.txt`: query rewriting.
- `reranker-system.txt`, `reranker-user.txt`: fragment selection (`{question}`, `{fragments}`).

## Running

### 1. Database

With Docker (creates the database and applies `db/schema.sql` on first start):

```bash
docker compose up -d
```

If you already run PostgreSQL locally on port 5432, stop it or change the published port. Without Docker, create the `rag_implmentation` database and apply the schema:

```bash
psql -U postgres -d rag_implmentation -f db/schema.sql
```

The app uses `ddl-auto=validate`: Hibernate checks the schema but never changes it.

### 2. Application

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

### 3. Ingest the documents

```bash
curl -X POST http://localhost:8080/api/documents/ingest
```

Reads `src/main/resources/documents/`. Safe to repeat: documents are replaced, not duplicated, and documents removed from the folder are deleted from the database. It refuses to run on an empty folder, so a wrong path cannot wipe the index.

### 4. Ask

```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "¿Qué hago si pierdo mi tarjeta?"}'
```

The response has `answer`, `sources` (document, chunk and distance) and `metrics` (retrieval and LLM latency, generation tokens, and rewriting + reranking tokens).

### 5. Ask with streaming (SSE)

```bash
curl -N "http://localhost:8080/api/rag/ask/stream?question=¿Qué hago si pierdo mi tarjeta?"
```

The answer arrives in pieces (`data: ...`) as the LLM writes it. In Postman, send a GET to the same URL.

`GET /api/documents/search?question=...` returns the raw vector search results, which helps when debugging retrieval.

## Evaluation

The dataset is in `src/test/resources/eval/eval-cases.json`: 30 questions with the literal evidence expected, split into `ajuste` (tuning, Q01–Q20) and `confirmacion` (held-out, Q21–Q30, only used to confirm an improvement is not overfitting).

| Test | What it measures | Cost |
|---|---|---|
| `RetrievalEvaluationTest` | Whether retrieved chunks contain the evidence; for unanswerable questions, that nothing is retrieved. | Free with Ollama; with Claude it pays for rewriting and reranking. |
| `GenerationEvaluationTest` | Correct answer/abstain decision, answers with markdown, latency, tokens and the real cost of the run. | ~US$0.02 per 30-question run with Haiku 5.5. |
| `JudgeEvaluationTest` | LLM-as-judge: faithfulness, completeness and relevance (1 to 5) for each answer. | A bit more than the previous one (generates and judges). |
| `DocumentChunkerTest`, `RealDocumentChunkingTest` | Chunking and sentence-based overlap. | Free. |

```bash
./mvnw test -Dtest=RetrievalEvaluationTest
./mvnw test -Dtest=GenerationEvaluationTest -Deval.split=ajuste        # or confirmacion; omit to run all 30
./mvnw test -Dtest=JudgeEvaluationTest -Deval.split=ajuste
```

Evaluation tests use the real database and LLM: configure the app and ingest the documents first. Check your API credit before running them with Claude.

## Project layout

```
db/schema.sql            database schema (pgvector)
docker-compose.yml       PostgreSQL + pgvector
src/main/java/com/albaro/rag/demo/
  config/      Ollama, embeddings and prompt properties
  controller/  RagController, DocumentController (thin: receive and delegate)
  dto/         RagResponse, RagMetrics, RetrievalOutcome, RerankOutcome...
  entity/      DocumentChunk (vector(1024))
  ingestion/   loading, chunking and ingestion
  llm/         LlmClient + Ollama and Claude adapters
  prompt/      prompt loading and rendering
  repository/  pgvector queries
  service/     retrieval, rewriting, reranking, RAG and streaming
src/main/resources/
  documents/   source documents
  prompts/     RAG prompts
src/test/java/com/albaro/rag/demo/eval/   evaluation and judge
src/test/resources/eval/                  30-question dataset
src/test/resources/prompts/               judge prompts
```
