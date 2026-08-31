# java-ai-golden

Отдельный слот **тестирования ИИ**, не ячейка пирамиды заметок и не папка внутри эталона / takeaway.

Объект качества — агент и AI-процесс QA.Guru, не логин приложения.

| Фаза | Что тестируем | Сейчас |
|------|----------------|--------|
| 1. Generation | Агент пишет автотест (golden + контракт без LLM в CI) | этот репозиторий |
| 2. Pack | Skills, rules, RAG как продукт automation | позже, пакет `eval.pack` |

Не строка матрицы UI/HTTP в `matrix.yaml`. Не копировать в `tests-java-gradle-junit5-allure3-selenide`.

## Прогон

```bash
cd projects/autotests-ai-multistack-home/java-ai-golden
./gradlew test
./gradlew test -DincludeTags=generation
```

Приложение заметок не нужно. Фикстуры — записанный ответ агента в `src/test/java/eval/generation/fixtures/`.

## Контракт vs судья

- `GenerationContractTest` — дискретные поля jsonl (`layer`, статус, `must_not`).
- `rubric-judge.md` — LLM-as-a-judge отдельным task, не этот Gradle-срез.

Красный демо: в `login-401-api.out.md` вставить `openPage()` — падает golden, продуктовый логин в другом репо не при чём.
