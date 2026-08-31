# Фаза 2 — pack как продукт

SUT — не логин и не сгенерированный Java, а **диета агента**: `src/test/resources/pack/`
(rules, skill, RAG, ADR, контекст PO).

CI без LLM. Live generation по-прежнему в `eval.generation`.

| Тест | Ловит |
|------|--------|
| `PackDietTest` | битый id, 0 или 5+ чанков, файл без YAML `id:` |
| `PackLayerIsolationTest` | UI-чанк в API-ретривере (`test-negative` → `submitExpectingError` в api) |
| `PackSkillContractTest` | вырезанные якоря: отказ, lab 36, «можно @Step в тесте» |

Красный демо офлайн: в `golden-generation.jsonl` у `login-401-api` добавить rag-id `test-negative` — упадёт isolation.

Не копировать takeaway и не гонять Ollama здесь.
