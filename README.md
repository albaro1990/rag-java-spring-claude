# rag-demo

Laboratorio de RAG (Retrieval-Augmented Generation) en Java 21 + Spring Boot que responde preguntas sobre procesos de tarjetas bancarias usando únicamente los documentos cargados. Si la información no está en los documentos, el sistema se abstiene en lugar de inventar.

Los resultados de evaluación, las decisiones tomadas y las lecciones aprendidas están en [EVALUACION.md](EVALUACION.md).

## Arquitectura

```
                 INGESTA (POST /api/documents/ingest)
documents/*.txt ─► DocumentLoader ─► DocumentChunker ─► Embeddings (bge-m3) ─► PostgreSQL + pgvector

                 CONSULTA (POST /api/rag/ask, GET /api/rag/ask/stream)
pregunta ─► QueryRewriter ─► búsqueda vectorial (pregunta original + reescrita)
        ─► LlmReranker (filtra candidatos) ─► prompt con contexto ─► LLM ─► respuesta + fuentes + métricas
```

| Etapa | Clase | Qué hace |
|---|---|---|
| Carga | `ingestion/DocumentLoader` | Lee los `.txt` y normaliza saltos de línea (`\r\n` → `\n`). |
| Chunking | `ingestion/DocumentChunker` | Corta por oraciones en bloques de ~500 caracteres. Soporta overlap por oraciones (hoy en 0). |
| Ingesta | `ingestion/DocumentIngestionService` | Calcula embeddings fuera de la transacción y reemplaza cada documento completo (idempotente: reingestar no duplica). |
| Búsqueda | `service/DocumentChunkService` | Distancia coseno en pgvector con umbral y límite. |
| Reescritura | `service/QueryRewriter` | Reformula la pregunta con vocabulario de los documentos; valida la salida (primera línea, máx. 300 caracteres). |
| Reranking | `service/LlmReranker` | El LLM elige qué candidatos responden la pregunta, o `NINGUNO`. |
| Orquestación | `service/RetrievalService`, `service/RagService` | Retrieval en dos etapas, armado del contexto, llamada al LLM y métricas de todas las llamadas. |
| Streaming | `service/RagStreamingService` | Envía la respuesta por SSE en hilos virtuales; el controller solo delega. |
| LLM | `llm/LlmClient` | Interfaz (puerto) con dos adaptadores: `OllamaLlmClient` y `ClaudeLlmClient`. |
| Prompts | `prompt/PromptTemplateService` | Carga los prompts desde `resources/prompts` y los guarda en caché. |

## Requisitos

- Java 21 (el proyecto trae Maven Wrapper, no hace falta instalar Maven).
- PostgreSQL con la extensión `pgvector` y una base llamada `rag_implmentation`.
- [Ollama](https://ollama.com) corriendo en `http://localhost:11434` con los modelos:
  ```bash
  ollama pull bge-m3        # embeddings (siempre necesario)
  ollama pull qwen2.5:3b    # LLM local (solo si rag.llm.provider=ollama)
  ```
- Para usar Claude: una API key de Anthropic con crédito.

## Configuración

Los secretos van en **variables de entorno**, nunca en el código, en `application.properties` ni en git:

| Variable | Uso |
|---|---|
| `DB_PASSWORD` | Contraseña de PostgreSQL. |
| `ANTHROPIC_API_KEY` | API key de Anthropic (solo con `rag.llm.provider=claude`). |

En Windows (PowerShell), una sola vez; luego reinicia IntelliJ o la terminal:

```powershell
setx DB_PASSWORD "tu_password"
setx ANTHROPIC_API_KEY "tu_api_key"
```

Propiedades principales de `src/main/resources/application.properties`:

| Propiedad | Valor | Para qué |
|---|---|---|
| `rag.llm.provider` | `claude` | `claude` u `ollama`: qué LLM genera, reescribe y rerankea. |
| `anthropic.model` / `anthropic.judge-model` | `claude-haiku-5-5` | Modelo generador y modelo juez. |
| `anthropic.timeout` / `anthropic.max-retries` | `30s` / `3` | Robustez ante una API lenta o caída. |
| `rag.chunk-size` / `rag.chunk-overlap` | `500` / `0` | Tamaño de chunk y overlap (ver EVALUACION.md). |
| `rag.retrieval.threshold` / `rag.retrieval.limit` | `0.40` / `8` | Retrieval de una etapa (cuando el reranker está apagado). |
| `rag.query-rewriting.enabled` | `true` | Activa la reescritura de la pregunta. |
| `rag.rerank.enabled` | `true` | Activa el retrieval en dos etapas. |
| `rag.rerank.candidate-threshold` / `candidates` / `top-n` | `0.45` / `15` / `5` | Etapa 1 amplia, etapa 2 se queda con máximo 5. |
| `rag.streaming.timeout-ms` | `120000` (por defecto) | Tiempo máximo de una respuesta en streaming. |
| `logging.level.com.albaro.rag.demo.service.*` | `INFO` / `DEBUG` | `DEBUG` muestra el contexto, la reescritura y la elección del reranker. |

Los prompts viven en `src/main/resources/prompts/` (guardar siempre con fin de línea **LF**: con CRLF el comportamiento del modelo cambió de forma medible):

- `rag-system.txt`, `rag-user.txt`: generación de la respuesta (`{context}`, `{question}`).
- `query-rewriter-system.txt`: reescritura de la pregunta.
- `reranker-system.txt`, `reranker-user.txt`: selección de fragmentos (`{question}`, `{fragments}`).

## Ejecutar

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

### 1. Cargar los documentos

```bash
curl -X POST http://localhost:8080/api/documents/ingest
```

Lee `src/main/resources/documents/`. Se puede repetir sin duplicar chunks.

### 2. Preguntar

```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "¿Qué hago si pierdo mi tarjeta?"}'
```

Respuesta: `answer`, `sources` (documento, chunk y distancia) y `metrics` (tiempos de retrieval y LLM, tokens de generación y de reescritura + rerank).

### 3. Preguntar en streaming (SSE)

```bash
curl -N "http://localhost:8080/api/rag/ask/stream?question=¿Qué hago si pierdo mi tarjeta?"
```

La respuesta llega en pedazos (`data: ...`) a medida que el LLM la escribe. En Postman: petición GET a la misma URL.

### Otros endpoints

- `GET /api/documents/search?question=...`: muestra los chunks que devuelve la búsqueda vectorial, útil para depurar retrieval.
- `POST /api/documents/chunks`, `POST /api/documents/chunk`, `GET /api/documents/ingestion-test`: endpoints de prueba del inicio del laboratorio. No deberían existir en producción (ver pendientes en EVALUACION.md).

## Evaluación

El dataset está en `src/test/resources/eval/eval-cases.json`: 30 preguntas con evidencia literal esperada, divididas en `ajuste` (Q01–Q20, para iterar) y `confirmacion` (Q21–Q30, solo para confirmar que una mejora no es sobreajuste).

| Test | Qué mide | Costo |
|---|---|---|
| `RetrievalEvaluationTest` | Si los chunks recuperados contienen la evidencia; en preguntas sin respuesta, que no recupere nada. | Gratis con Ollama; con Claude paga reescritura y rerank. |
| `GenerationEvaluationTest` | Decisión correcta (responder o abstenerse), respuestas con markdown, latencia, tokens y costo real de la corrida. | ~US$ 0.02 por corrida de 30 con Haiku 5.5. |
| `JudgeEvaluationTest` | LLM como juez: fidelidad, completitud y relevancia (1 a 5) de cada respuesta. | Algo más que el anterior (genera y juzga). |
| `DocumentChunkerTest`, `RealDocumentChunkingTest` | Chunking y overlap por oraciones. | Gratis. |

```bash
./mvnw test -Dtest=RetrievalEvaluationTest
./mvnw test -Dtest=GenerationEvaluationTest -Deval.split=ajuste        # o confirmacion; sin -D corre las 30
./mvnw test -Dtest=JudgeEvaluationTest -Deval.split=ajuste
```

Los tests de evaluación usan la base y el LLM reales: hay que tener la aplicación configurada y los documentos ingeridos. Antes de correr con Claude, revisa el crédito disponible.

## Estructura

```
src/main/java/com/albaro/rag/demo/
  config/      Ollama, embeddings y propiedades de prompts
  controller/  RagController, DocumentController (delgados: reciben y delegan)
  dto/         RagResponse, RagMetrics, RetrievalOutcome, RerankOutcome...
  entity/      DocumentChunk (vector(1024))
  ingestion/   carga, chunking e ingesta
  llm/         LlmClient + adaptadores Ollama y Claude
  prompt/      carga y renderizado de prompts
  repository/  consultas pgvector
  service/     retrieval, reescritura, reranker, RAG y streaming
src/main/resources/
  documents/   documentos fuente
  prompts/     prompts del RAG
src/test/java/com/albaro/rag/demo/eval/   evaluación y juez
src/test/resources/eval/                  dataset de 30 preguntas
```
