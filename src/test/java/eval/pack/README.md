# Фаза 2 — pack как продукт

SUT — не логин и не сгенерированный Java, а **диета агента**: `src/test/resources/pack/`
(rules, skill, RAG, ADR, контекст PO) и **лексический ретривер**.

CI без LLM. Live generation берёт чанки из `LexicalRetriever.retrieve(prompt)`, не из jsonl.

| Тест | Ловит |
|------|--------|
| `PackDietTest` | битый id, 0 или 5+ чанков, пустой `index:`, `related` на несуществующий файл |
| `PackLayerIsolationTest` | UI-чанк в API-golden (`test-negative` → `submitExpectingError` в api) |
| `PackSkillContractTest` | вырезанные якоря: отказ, lab 36, «можно @Step в тесте» |
| `RetrieverTest` | ретривер ≠ golden; «Не e2e» не отфильтровало форму; отравленный `index` у `po-fluent` |

Ретривер: пересечение токенов по YAML `index` / tags / id / заголовок, затем `related` до 2–4 id. Тело чанка (Java-сниппеты) **не** индексируется — иначе `wrongpassword` склеивает слои.

Красный демо офлайн:

- в `golden-generation.jsonl` у `login-401-api` добавить rag-id `test-negative` — isolation;
- в `po-fluent` `index:` дописать `неуспешный` — `RetrieverTest` poison.

Не копировать takeaway и не гонять Ollama здесь.
