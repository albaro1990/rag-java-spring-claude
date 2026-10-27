# RAG evaluation

History of measurements, decisions and lessons from the lab. Working rule: **measure before changing, change one variable at a time, and re-run the same evaluation**.

## How it is measured

- **Dataset**: 30 questions (`src/test/resources/eval/eval-cases.json`) with literal expected evidence. Categories: `directa` (direct), `parafrasis` (paraphrase), `compuesta` (multi-part), `entre_documentos` (cross-document), `parcial` (partial answer) and `sin_respuesta` (7 unanswerable questions where the right behaviour is to abstain, including traps such as the PIN or phone support hours).
- **Split**: `ajuste` (tuning, Q01–Q20) is used to iterate; `confirmacion` (held-out, Q21–Q30) only to verify. If an improvement helps on tuning but not on held-out, it is overfitting.
- **Retrieval**: a case passes if the retrieved chunks contain all the expected evidence; for `sin_respuesta`, if nothing is retrieved.
- **Generation**: the answer-or-abstain decision is checked automatically; content quality is scored by the judge.
- **Judge**: an LLM with structured output that scores faithfulness, completeness and relevance from 1 to 5.
- **Operations**: retrieval and LLM latency, tokens from every LLM call, and cost per question.

## Results history

### Retrieval

| # | Change | Dataset | Result |
|---|---|---|---|
| 1 | Baseline: all-MiniLM-L6-v2 embeddings (English), threshold 0.50 | 10 | 5/10 |
| 2 | Multilingual bge-m3 embeddings (1024 dims), threshold 0.50 | 10 | 5/10 |
| 3 | bge-m3, threshold 0.40, limit 8 | 10 | 8/10 |
| 4 | LF loader + real sentence-based overlap of 100 (23 chunks) | 10 | 7/10 |
| 5 | Overlap 0 (16 chunks) | 10 | 7/10 |
| 6 | New baseline with 30 questions | 30 | 23/30 (tuning 15/20, held-out 8/10) |
| 7 | + Query rewriting (Haiku 5.5) | 30 | 24/30 |
| 8 | + Two-stage LLM reranker (Haiku 5.5) | 30 | **29/30** (held-out 10/10) |

### Generation

| # | Generator | Dataset | Correct decision | Hallucinations |
|---|---|---|---|---|
| 1 | qwen2.5:3b (local) | 10 | 9/10 | 0 |
| 2 | qwen2.5:3b, prompt rule 3 removed and prompts in LF | 10 | 10/10 | 0 |
| 3 | Opus 5.5 / Sonnet 5.5 / Haiku 5.5 | 10 | 10/10 each | 0 |
| 4 | Sonnet 5.5, baseline | 30 | 28/30 | 0 |
| 5 | Haiku 5.5, full pipeline (rewriting + reranker) | 30 | 30/30 | 0 |
| 6 | Haiku 5.5, same configuration, another run | 30 | 29/30 (Q19 flipped) | 0 |

### Judge (average faithfulness / completeness / relevance)

| Generator | Judge | Answers | Faithfulness | Completeness | Relevance |
|---|---|---|---|---|---|
| qwen2.5:3b | Sonnet 5.5 | 7 | 4.29 | 4.14 | 4.43 |
| Opus 5.5 | Sonnet 5.5 | 7 | 5.00 | 4.86 | 4.71 |
| Sonnet 5.5 | Sonnet 5.5 | 7 | 5.00 | 4.71 | 4.43 |
| Haiku 5.5 | Sonnet 5.5 | 7 | 4.71 | 5.00 | 4.57 |
| Sonnet 5.5 (30 questions) | Sonnet 5.5 | 21 | 5.00 | 4.86 | 4.57 |
| Haiku 5.5, full pipeline | Haiku 5.5 | 23 | 5.00 | 4.87 | 4.74 |

### Latency and cost

| Configuration | LLM average / max | Cost per question |
|---|---|---|
| qwen2.5:3b local (CPU) | 43.6 s / 77.5 s | US$0 |
| Haiku 5.5, generation only | 1.8 s / 4.8 s | US$0.0003 |
| Sonnet 5.5, generation only | 2.1 s / 4.2 s | US$0.0054 |
| Opus 5.5, generation only | 3.9 s / 6.5 s | US$0.0124 |
| Haiku 5.5, full pipeline | retrieval 3.5 s + LLM 2.7 s | **US$0.00069** (all calls) |

With the full pipeline, 66% of input tokens come from rewriting and reranking. With streaming, the first piece of the answer arrives after about 3 s (time to first token).

## Decisions

| Decision | Why |
|---|---|
| bge-m3 embeddings instead of MiniLM | MiniLM is English-only: irrelevant chunks ended up closer (0.35) than relevant ones (0.445). |
| Threshold 0.40, limit 8 | With bge-m3 a 0.50 threshold filtered nothing. |
| Overlap 0 | Overlap 100 filled the top 8 with repeated text and did not improve retrieval (7/10 both ways); generation was better with 0. The code still supports overlap. |
| Idempotent ingestion | Re-ingesting duplicated chunks; now each document is replaced as a whole in one transaction, and documents removed from the folder are deleted. |
| Abstention detected by exact match | With `contains`, a partial answer that included the "no information" sentence was counted as an abstention. |
| Rule 8: plain text, no markdown | Answers with markdown dropped from 8 to 0, at the cost of +35% output tokens. |
| Haiku 5.5 as generator and judge | Same faithfulness as larger models on this dataset, the fastest, and about 18 times cheaper than Sonnet. The self-preference bias of judging its own answers was accepted for budget reasons. |
| Query rewriting + reranker | Fixed vocabulary mismatches (Q15 "no me aprueban" vs "rechazada", Q28), and the reranker removed noise on unanswerable questions by answering `NINGUNO`. |
| Revert the reranker's "partial information" rule | It fixed Q19 but broke Q09: it accepted a vague fragment and opened the door to hallucination. In banking, missing a partial answer is better than answering wrongly. |
| Thin controllers | Threading, SSE and error handling for streaming live in `RagStreamingService`. |

## Lessons

1. **Formatting details change results.** With CRLF line endings the paragraph split failed and the same prompt scored 9/10 instead of 10/10.
2. **One variable at a time.** A prompt experiment was contaminated by an uncontrolled LF change.
3. **Retrieval is the bottleneck.** With Claude, generation failures almost always came from retrieval missing the evidence, not from the model making things up.
4. **Small models invent steps.** Qwen 3B added "activar" (activate) and "reportar la pérdida" (report the loss), which are not in the documents; Claude did not invent anything in any run.
5. **The held-out split detects overfitting.** The baseline scored 15/20 on tuning and 8/10 on held-out: similar proportions.
6. **Every improvement has a cost.** The reranker raised retrieval from 24 to 29, but retrieval latency went from ~150 ms to ~3.5 s and cost per question went up.
7. **Measure the full cost.** At first only the generation call was counted; with rewriting and reranking the real cost was 2 to 3 times higher.
8. **Validate the output of intermediate LLM calls.** Once the rewriter returned an extra paragraph; now only the first line is kept, up to 300 characters.
9. **LLMs are not deterministic.** Q19 passed in one run and failed in the next with no changes: a single-run result is noisy.
10. **API prompt caching does not always apply.** Haiku 5.5 requires a minimum prefix length and our system prompt is shorter, so only the in-memory file cache is used.

## Known limitations

- Small dataset (30 synthetic questions, 3 documents): differences of one or two questions may be noise.
- Each configuration was run once; the right approach is several runs and reporting the average.
- The judge is the same model as the generator (self-preference bias).
- Q19 (partial information) depends on the run.
- Chunk 4 of `proceso_obtener_tarjeta` is a "hub" chunk that lands close to many questions without answering them.

## Next steps toward production

**RAG quality**

- Cross-encoder reranker (cheaper and faster than an LLM).
- Hybrid search: vector + keyword (BM25) combined with Reciprocal Rank Fusion.
- Result diversity (MMR) to avoid repeated or "hub" chunks.
- Lower the rewriter's thinking effort to spend fewer tokens.
- More questions, ideally real user questions, and a judge different from the generator.

**Code**

- Validate configuration at startup with `@ConfigurationProperties` + `@Validated`.
- Return an ingestion summary from `/ingest` instead of the full entities.
- Schema migrations with Flyway instead of a single `schema.sql`.

**Operations**

- Typed JSON SSE events (`delta` and `done` with sources and metrics).
- Observability: Micrometer metrics (time to first token, duration, tokens, cost), structured JSON logs with a correlation id (MDC), and no personal data at INFO level.
- Circuit breaker and fallback to another provider if the API fails persistently.
- Run the evaluation in CI as a regression test before every prompt or model change.

## Experimento: rag-system v2 (contexto + porqué)

**Hipótesis:** añadir contexto explícito del asistente bancario y justificar la prioridad de la evidencia reducirá respuestas no fundamentadas sin aumentar las abstenciones innecesarias.

**Variable modificada:** únicamente el contenido de `rag-system.txt`: se añade el bloque `CONTEXTO` y el bloque `PORQUÉ`. Se mantienen iguales el modelo Haiku 5.5, parámetros, preguntas, documentos, recuperación, reescritura, reranking y demás configuración. Se compara contra la versión actual del prompt.

**Protocolo:** ejecutar versión actual y v2 tres veces cada una. Registrar los resultados por corrida, no solo el total agregado. Usar Q01–Q20 para ajuste y Q21–Q30 como conjunto de confirmación; no inspeccionar Q21–Q30 para modificar el prompt. Comparar ambas versiones sobre los mismos casos y condiciones.

**Acepto el cambio si, en 3 corridas:**

- En las 30 evaluaciones de confirmación de la versión v2 (10 preguntas × 3 corridas), hay **0 alucinaciones críticas**: ninguna afirmación inventada sobre tarifas, requisitos, plazos, aprobaciones o procedimientos bancarios.
- Cada corrida de confirmación obtiene al menos **9/10 decisiones correctas** (responder cuando hay evidencia suficiente y abstenerse cuando no la hay), y el promedio de v2 no queda por debajo del promedio de la versión actual.
- La tasa de abstenciones innecesarias en preguntas respondibles no aumenta frente a la versión actual; cualquier cambio de clasificación se revisa contra la evidencia esperada de cada pregunta.
- Las puntuaciones medias de `faithfulness`, `completeness` y `relevance` del juez no empeoran más de **0.2 puntos sobre 5** respecto de la versión actual; toda posible alucinación señalada por el juez se revisa manualmente.
- El coste medio total por pregunta, incluyendo reescritura, reranking y generación, no aumenta más de **15 %**, y la latencia media total no aumenta más de **20 %**, salvo que exista una mejora de calidad clara y documentada que justifique la diferencia.
- Se guardan por separado el prompt/versionado, resultados por pregunta, respuestas, decisiones de abstención, puntuaciones del juez, tokens, coste y latencia para que el experimento sea reproducible.

**Rechazo el cambio si** aparece una alucinación crítica nueva, empeoran las decisiones de responder/abstenerse en confirmación, o se incumplen los límites de coste/latencia sin una mejora de calidad suficiente.

**Resultado inconcluso si** las diferencias son pequeñas o inconsistentes entre corridas. En ese caso no se acepta todavía el cambio: se revisan los casos discordantes y se amplía el conjunto de evaluación, sin reutilizar las preguntas de confirmación para ajustar el prompt.

**Nota:** el conjunto de confirmación tiene solo 10 preguntas distintas; tres corridas ayudan a observar variabilidad, pero no equivalen a 30 casos independientes ni demuestran por sí solas una mejora estadística generalizable.

### Resultados (26-oct-2026, Haiku 5.5, 3 corridas por versión)

| Medida | v1 (actual) | v2 (contexto + porqué) |
| --- | --- | --- |
| Decisiones correctas (30 preguntas) | 29, 29, 29 | 30, 29, 29 |
| Alucinaciones (respondió sin evidencia) | 0 | 0 |
| Abstenciones de más | 3 (Q19 en las 3) | 2 (Q19 en 2 de 3) |
| Confirmación Q21–Q30 | 10/10 en las 3 | 10/10 en las 3 |
| Juez: fidelidad / completitud / relevancia | 5.00 / 4.85 / 4.62 | 5.00 / 4.95 / 4.60 |
| Tokens por corrida (entrada / salida) | 103,235 / 17,618 | 108,081 / 19,769 |
| Costo por pregunta | US$0.00064 | US$0.00069 (+8%) |
| Latencia total media | 5.6 s | 5.9 s (+5%) |

**Decision:**
- Se mantiene v1. v2 es no inferior pero no mejora, y cuesta un 8% más; se conserva como candidata documentada.”
