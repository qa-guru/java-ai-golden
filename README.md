# java-ai-golden

Репозиторий: [qa-guru/java-ai-golden](https://github.com/qa-guru/java-ai-golden).

**Mill занятия:** [START.md](START.md) — без модели, live смоук, красный 7b на adversarial.

Отдельный слот **тестирования ИИ**, не ячейка пирамиды заметок и не папка внутри эталона / takeaway.

Объект качества — агент и AI-процесс QA.Guru, не логин приложения.

| Фаза | Что тестируем | Где |
|------|----------------|-----|
| 1. Generation | Агент пишет автотест | `eval.generation` |
| 2. Pack | Skills, rules, RAG, ретривер | `eval.pack` |

Не строка матрицы UI/HTTP в `matrix.yaml`. Не копировать в `tests-java-gradle-junit5-allure3-selenide`.

## Прогон

```bash
git clone https://github.com/qa-guru/java-ai-golden.git
cd java-ai-golden
./gradlew test
./gradlew test -Dlive=true -DincludeTags=live
./gradlew test -Dlive=true -DincludeTags=live -Dadversarial=true
```

Нужен локальный Ollama. Модель по умолчанию `qwen2.5-coder:7b`. Другая: `-Dmodel=…`. Судью выключить: `-Djudge=false`. Перезаписать фикстуры: `-DwriteFixtures=true` (не коммитить эхо 7b).

Диета пака: `src/test/resources/pack/`.

## Контракт vs судья vs ретривер

- `GenerationContractTest` — фикстуры, без LLM.
- `ContractAssertionsTest` / `JudgeParseTest` / `WorkflowPromptTest` — регрессии грейдера и промпта.
- `LiveGenerationContractTest` — generate → контракт по **RAW** → `Judge` (вердикт в лог, `REJECTED` live не валит). Галлюцинации только с `-Dadversarial=true`.
- `RetrieverTest` — jsonl `expect.rag` как оракул лексического ретривера, не как подстановка в промпт и не как assert генерации.
- Судья по MODE: негатив формы, happy path, API.
- Канон: лаборатория 36; JSON — не `Unauthorized`. e2e golden id — история формы (`login-wrong-password-e2e`), не HTTP 401.

Красный демо офлайн: `@Step` на методе `*Tests` или `fillAndSubmitForm` в `login-wrong-password-e2e` фикстуре. Pack: `test-negative` в API `expect.rag` или «неуспешный» в `index:` у `po-fluent`. Adversarial live: эхо `Invalid password`, over-refuse селектора.
