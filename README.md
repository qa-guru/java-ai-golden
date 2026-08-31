# java-ai-golden

**Старт:** [START.md](START.md) — без модели, потом live, куда смотреть лог.

Отдельный слот **тестирования ИИ**, не ячейка пирамиды заметок и не папка внутри эталона / takeaway.

Объект качества — агент и AI-процесс QA.Guru, не логин приложения.

| Фаза | Что тестируем | Сейчас |
|------|----------------|--------|
| 1. Generation | Агент пишет автотест (golden + контракт) | этот репозиторий |
| 2. Pack | Skills, rules, RAG как продукт automation | позже, пакет `eval.pack` |

Не строка матрицы UI/HTTP в `matrix.yaml`. Не копировать в `tests-java-gradle-junit5-allure3-selenide`.

## Прогон

Записанные фикстуры (без модели, CI):

```bash
cd projects/autotests-ai-multistack-home/java-ai-golden
./gradlew test
```

Полный цикл: исполнить workflow (rules + skill + RAG → Ollama) и сразу проверить golden:

```bash
./gradlew test -Dlive=true -DincludeTags=live
```

Нужен локальный Ollama. Модель по умолчанию `qwen2.5-coder:7b`. Другая: `-Dmodel=…`. Перезаписать фикстуры ответом модели: `-DwriteFixtures=true`.

Диета пака для live лежит в `src/test/resources/pack/` (не takeaway и не эталон).

## Контракт vs судья

- `GenerationContractTest` — фикстуры.
- `LiveGenerationContractTest` — generate + тот же `ContractAssertions`.
- `rubric-judge.md` — LLM-as-a-judge на открытый текст, не этот Gradle-срез.

Красный демо офлайн: в `login-401-api.out.md` вставить `openPage()`. Live: сломанный skill в `pack/` → падает контракт.
