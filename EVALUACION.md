# Evaluación del RAG

Historial de mediciones, decisiones y lecciones del laboratorio. Regla de trabajo: **medir antes de cambiar, cambiar una sola variable a la vez y volver a correr la misma evaluación**.

## Cómo se mide

- **Dataset**: 30 preguntas (`src/test/resources/eval/eval-cases.json`) con evidencia literal esperada. Categorías: `directa`, `parafrasis`, `compuesta`, `entre_documentos`, `parcial` y `sin_respuesta` (7 preguntas donde lo correcto es abstenerse, incluidas trampas como el PIN o el horario telefónico).
- **Split**: `ajuste` (Q01–Q20) se usa para iterar; `confirmacion` (Q21–Q30) solo para verificar. Si una mejora sube en ajuste y no en confirmación, es sobreajuste.
- **Retrieval**: un caso pasa si los chunks recuperados contienen toda la evidencia esperada; en `sin_respuesta`, si no se recupera nada.
- **Generación**: decisión correcta (responder o abstenerse) automática; el contenido lo revisa el juez.
- **Juez**: LLM con salida estructurada que puntúa fidelidad, completitud y relevancia de 1 a 5.
- **Operación**: latencia de retrieval y LLM, tokens de todas las llamadas y costo por pregunta.

## Historial de resultados

### Retrieval

| # | Cambio | Dataset | Resultado |
|---|---|---|---|
| 1 | Línea base: embeddings all-MiniLM-L6-v2 (inglés), umbral 0.50 | 10 | 5/10 |
| 2 | Embeddings multilingües bge-m3 (1024 dims), umbral 0.50 | 10 | 5/10 |
| 3 | bge-m3, umbral 0.40, límite 8 | 10 | 8/10 |
| 4 | Loader LF + overlap real de 100 por oraciones (23 chunks) | 10 | 7/10 |
| 5 | Overlap 0 (16 chunks) | 10 | 7/10 |
| 6 | Nueva línea base con 30 preguntas | 30 | 23/30 (ajuste 15/20, confirmación 8/10) |
| 7 | + Reescritura de la pregunta (Haiku 5.5) | 30 | 24/30 |
| 8 | + Reranker LLM en dos etapas (Haiku 5.5) | 30 | **29/30** (confirmación 10/10) |

### Generación

| # | Generador | Dataset | Decisión correcta | Alucinaciones |
|---|---|---|---|---|
| 1 | qwen2.5:3b local | 10 | 9/10 | 0 |
| 2 | qwen2.5:3b, sin regla 3 del prompt y prompts en LF | 10 | 10/10 | 0 |
| 3 | Opus 5.5 / Sonnet 5.5 / Haiku 5.5 | 10 | 10/10 cada uno | 0 |
| 4 | Sonnet 5.5, línea base | 30 | 28/30 | 0 |
| 5 | Haiku 5.5, pipeline completo (reescritura + reranker) | 30 | 30/30 | 0 |
| 6 | Haiku 5.5, misma configuración, otra corrida | 30 | 29/30 (Q19 cambió) | 0 |

### Juez (promedio fidelidad / completitud / relevancia)

| Generador | Juez | Respuestas | Fidelidad | Completitud | Relevancia |
|---|---|---|---|---|---|
| qwen2.5:3b | Sonnet 5.5 | 7 | 4.29 | 4.14 | 4.43 |
| Opus 5.5 | Sonnet 5.5 | 7 | 5.00 | 4.86 | 4.71 |
| Sonnet 5.5 | Sonnet 5.5 | 7 | 5.00 | 4.71 | 4.43 |
| Haiku 5.5 | Sonnet 5.5 | 7 | 4.71 | 5.00 | 4.57 |
| Sonnet 5.5 (30 preguntas) | Sonnet 5.5 | 21 | 5.00 | 4.86 | 4.57 |
| Haiku 5.5, pipeline completo | Haiku 5.5 | 23 | 5.00 | 4.87 | 4.74 |

### Latencia y costo

| Configuración | LLM promedio / máximo | Costo por pregunta |
|---|---|---|
| qwen2.5:3b local (CPU) | 43.6 s / 77.5 s | US$ 0 |
| Haiku 5.5, solo generación | 1.8 s / 4.8 s | US$ 0.0003 |
| Sonnet 5.5, solo generación | 2.1 s / 4.2 s | US$ 0.0054 |
| Opus 5.5, solo generación | 3.9 s / 6.5 s | US$ 0.0124 |
| Haiku 5.5, pipeline completo | retrieval 3.5 s + LLM 2.7 s | **US$ 0.00069** (todas las llamadas) |

Con el pipeline completo, el 66 % de los tokens de entrada son de reescritura + reranker. En streaming, el primer pedazo de la respuesta llega a unos 3 s (TTFT).

## Decisiones

| Decisión | Por qué |
|---|---|
| Embeddings bge-m3 en vez de MiniLM | MiniLM es de inglés: chunks irrelevantes quedaban más cerca (0.35) que los relevantes (0.445). |
| Umbral 0.40, límite 8 | Con bge-m3 el umbral 0.50 no filtraba nada. |
| Overlap 0 | Overlap 100 llenaba el top 8 con texto repetido y no mejoró retrieval (7/10 en ambos); generación fue mejor con 0. El código sigue soportando overlap. |
| Ingesta idempotente | Reingestar duplicaba chunks; ahora cada documento se reemplaza completo en una transacción. |
| Detector de abstención por igualdad exacta | Con `contains` una respuesta parcial que incluía la frase de "no hay información" se contaba como abstención. |
| Regla 9: texto plano, sin markdown | Las respuestas con markdown bajaron de 8 a 0, a cambio de +35 % tokens de salida. |
| Haiku 5.5 como generador y juez | Misma fidelidad que modelos más caros en este dataset, el más rápido y unas 18 veces más barato que Sonnet. Aceptamos el sesgo de que se juzgue a sí mismo por presupuesto. |
| Reescritura + reranker | Resolvieron fallos de vocabulario (Q15 "no me aprueban" ↔ "rechazada", Q28) y el reranker eliminó el ruido en las preguntas sin respuesta (dice `NINGUNO`). |
| Revertir la regla de "información parcial" del reranker | Arregló Q19 pero rompió Q09: aceptó un fragmento vago y abrió la puerta a alucinar. En banca, perder una respuesta parcial es mejor que responder mal. |
| Controllers delgados | La lógica de hilos, SSE y errores del streaming vive en `RagStreamingService`. |

## Lecciones

1. **Los detalles de formato cambian resultados.** Con fin de línea CRLF el split por párrafos fallaba y el mismo prompt daba 9/10 en vez de 10/10.
2. **Una variable a la vez.** Un experimento de prompt quedó contaminado por un cambio de LF no controlado.
3. **El retrieval es el cuello de botella.** Los fallos de generación con Claude fueron casi siempre porque el retrieval no trajo la evidencia, no porque el modelo inventara.
4. **Los modelos chicos inventan pasos.** Qwen 3B agregó "activar" y "reportar la pérdida", que no están en los documentos; Claude no inventó en ninguna corrida.
5. **El split de confirmación detecta sobreajuste.** La línea base dio 15/20 en ajuste y 8/10 en confirmación: proporciones similares.
6. **Toda mejora tiene un costo.** El reranker subió retrieval de 24 a 29, pero la latencia de retrieval pasó de ~150 ms a ~3.5 s y el costo por pregunta subió.
7. **Medir el costo completo.** Al principio solo contábamos la llamada de generación; con reescritura y rerank el costo real era 2 a 3 veces mayor.
8. **Validar la salida de los LLM intermedios.** Una vez el reescritor devolvió un párrafo extra; ahora se toma la primera línea con un máximo de 300 caracteres.
9. **Los LLM no son deterministas.** Q19 pasó en una corrida y falló en la siguiente sin cambiar nada: un resultado con una sola corrida tiene ruido.
10. **El caché de prompts de la API no siempre aplica.** Haiku 5.5 exige un prefijo mínimo de tokens y nuestro prompt de sistema es más corto, así que se cachea solo la lectura de archivos en memoria.

## Limitaciones conocidas

- Dataset chico (30 preguntas sintéticas, 3 documentos): las diferencias de una o dos preguntas pueden ser ruido.
- Cada configuración se corrió una sola vez; lo correcto es varias corridas y reportar el promedio.
- El juez es el mismo modelo que el generador (sesgo de autopreferencia).
- Q19 (información parcial) depende de la corrida.
- El chunk 4 de `proceso_obtener_tarjeta` es un "hub" que aparece cerca de muchas preguntas sin responderlas.

## Pendientes para llevarlo a producción

**Código del laboratorio**

- Quitar o proteger los endpoints de prueba de `DocumentController` (`/chunk`, `/ingestion-test`, `/chunks`).
- Validar la configuración al arrancar con `@ConfigurationProperties` + `@Validated`.
- Usar git y hacer commit después de cada experimento.

**Calidad del RAG**

- Reranker con cross-encoder (más barato y rápido que un LLM).
- Búsqueda híbrida: vectorial + palabras clave (BM25) combinadas con Reciprocal Rank Fusion.
- Diversidad en los resultados (MMR) para evitar chunks repetidos o "hub".
- Ajustar el esfuerzo de pensamiento del reescritor para gastar menos tokens.
- Más preguntas, idealmente reales de usuarios, y juez distinto al generador.

**Operación**

- Eventos SSE tipados en JSON (`delta` y `done` con fuentes y métricas).
- Observabilidad: métricas con Micrometer (TTFT, duración, tokens, costo), logs estructurados en JSON con un id de correlación (MDC) y nada de datos personales en INFO.
- Circuit breaker y fallback a otro proveedor si la API falla de forma sostenida.
- Correr la evaluación en CI como prueba de regresión antes de cada cambio de prompt o modelo.
