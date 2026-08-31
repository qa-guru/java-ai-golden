# С чего начать

Слот оценивает **генерацию автотестов** (AI-first workflow), не логин приложения.  
Эталон Selenide и takeaway сюда не копировать.

Нужны: Java 21, Gradle Wrapper в этой папке, для live — локальный Ollama и модель `qwen2.5-coder:7b`.

## 1. Открыть проект

Корень:

`projects/autotests-ai-multistack-home/java-ai-golden/`

Три файла, с которых смотреть:

| Файл | Зачем |
|------|--------|
| `src/test/java/eval/generation/golden-generation.jsonl` | контракт: промпт → слой / запреты |
| `src/test/java/eval/generation/fixtures/` | записанный эталон ответа (не live) |
| `src/test/resources/pack/` | диета workflow: rules + skill + RAG |

## 2. Сначала без модели

```bash
cd projects/autotests-ai-multistack-home/java-ai-golden
./gradlew test
```

Ожидание: 5 тестов `GenerationContractTest`, зелёные. Модель не вызывается.  
Это регрессия контракта: jsonl ↔ фикстура.

Красный демо: в `fixtures/login-401-api.out.md` вставить `openPage()`, снова `./gradlew test` — упадёт `must_not`.

## 3. Полный цикл: исполнить → проверить

Ollama должен быть запущен. Затем:

```bash
./gradlew test -Dlive=true -DincludeTags=live
```

Ожидание: 5 тестов `LiveGenerationContractTest`, ~15–30 с.

Цепочка: промпт из jsonl → `pack/` → Ollama → `ContractAssertions` (тот же, что у фикстур).

Ответ модели **не** пишется в `LoginTests.java`. Куда смотреть:

- stdout Gradle (`===== LIVE <id> =====`);
- `build/live-out/<id>.out.md` (после прогона).

Другая модель: `-Dmodel=…`.  
Затереть фикстуры live-ответом (редко): `-DwriteFixtures=true`.

## 4. Как читать результат

Контракт смотрит **дискретные** поля, не «байт в байт как эталон»:

- слой `@Layer("api")` / `"e2e"`;
- `401`, имя класса, RAG-id — если они есть в jsonl;
- `must_not`: `openPage(`, `testE2e`, `git commit`.

Зелёный live ≠ каноничный тест продукта. Модель может выдумать `LoginApiTests` и текст ошибки — контракт это пропустит. Для «канонично ли» — `src/test/java/eval/generation/rubric-judge.md` (судья, не этот Gradle).

Строки `read-all-rag` и `jailbreak-env` — отказ: red team / injection, не генерация логина.

## 5. Две минуты на занятии

1. `./gradlew test` — golden без LLM.
2. Live на `login-401-api` (или весь `-DincludeTags=live`) — открыть `build/live-out/`.
3. Сказать вслух: фикстура = CI; live = деградация после смены модели; судья = когда нельзя `contains`.

## Не делать

- Не класть это в `tests-java-gradle-junit5-allure3-selenide` и не в takeaway `main`.
- Не ждать, что live вльёт файл в пирамиду.
- Не гонять live на каждый PR (флейк и токены). CI — только шаг 2.

Фаза `eval.pack` (skills/rules как продукт) — позже, см. `src/test/java/eval/pack/README.md`.
