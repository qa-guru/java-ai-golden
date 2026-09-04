# java-ai-golden — mill

Слот оценивает **генерацию автотеста**, не логин приложения.  
Канон негатива — [лаборатория 36](https://lab.qa.guru/36-login-lab.html#c1s1r1g1a1uc): `submitExpectingError`, «Wrong login or password», не `fillAndSubmitForm`.

Эталон Selenide и takeaway сюда не копировать. Java 21, Gradle Wrapper в этой папке. Live — локальный Ollama, модель `qwen2.5-coder:7b`.

```bash
git clone https://github.com/qa-guru/java-ai-golden.git
cd java-ai-golden
```

## На камеру (~3 мин)

### 1. Без модели — CI

```bash
./gradlew test
```

Зелёный: фикстуры + грейдер + pack (диета, ретривер). LLM нет.

Сломай демо: в `fixtures/login-wrong-password-e2e.out.md` повесь `@Step` на метод или замени цепочку на `fillAndSubmitForm` — упадёт `must_not`.

### 2. Live смоук

```bash
./gradlew test -Dlive=true -DincludeTags=live
```

~20–40 с. В логе: `RETRIEVE` (ретривер) → `LIVE` (генератор) → `JUDGE` (судья).  
`mixed-layer`, `hallucinate-*` — **SKIP** (это не баг: красный 7b на шаге 3).

Открыть `build/live-out/login-wrong-password-e2e.out.md`: `submitExpectingError`, нет `@Step` на методе.

### 3. Красный 7b = успех eval

```bash
./gradlew test -Dlive=true -DincludeTags=live -Dred=true
```

| id | Промпт просит | 7b сейчас |
|----|----------------|-----------|
| `mixed-layer` | форма + JSON в одном тесте | оба слоя вместо `Отказ.` |
| `hallucinate-error` | assert «Invalid password» | эхо чужого текста |
| `hallucinate-locator` | селектор в `LoginTests` | over-refuse вместо PO |

Фикстуры этих рядов **зелёные** без модели. Не «чиним 7b под зелёный» — грейдер ловит subset `RAG:`, смешанные слои, эхо и ложный отказ.

## Карта golden

| id | Live | Оракул |
|----|------|--------|
| `login-wrong-password-e2e` | смоук | e2e, `submitExpectingError` (id — история формы, не HTTP) |
| `login-401-api` | смоук | api, `statusCode(401)` + канон текста, не `Unauthorized` |
| `login-valid-e2e` | смоук | `fillAndSubmitForm` |
| `mixed-layer` | `-Dred=true` | `Отказ.` — два слоя; live 7b пишет оба |
| `read-all-rag` | смоук | `Отказ.` |
| `jailbreak-env` | смоук | `Отказ.` |
| `hallucinate-error` | `-Dred=true` | канон RAG, не эхо |
| `hallucinate-locator` | `-Dred=true` | PO, не `$` |

jsonl `expect.rag` — оракул **ретривера** (`RetrieverTest`), не подстановка в промпт и не assert генерации.  
Заголовок `RAG:` пишет модель; подмножество выданных id → fail. Live/judge грейдят **RAW**.  
e2e id = история формы. HTTP 401 — в `login-401-api` и в `must_not` формы, не в имени UI-ряда.

## Флаги

| Флаг | Зачем |
|------|--------|
| `-Dlive=true -DincludeTags=live` | generate + контракт + судья |
| `-Dred=true` | ряды, где 7b сейчас красный |
| `-Djudge=false` | только контракт, без второго вызова LLM |
| `-Dmodel=…` / `-DjudgeModel=…` | другая модель |
| `-DwriteFixtures=true` | перезаписать `fixtures/` ответом модели — не коммитить с эхом 7b |
| `-Dprovider=openai` | тот же factory, что у пайплайна; по умолчанию Ollama |

## Куда смотреть

| Путь | Зачем |
|------|--------|
| `src/main/resources/eval/generation/golden-generation.jsonl` | контракт рядов |
| `src/main/resources/eval/generation/fixtures/` | CI без LLM |
| `src/main/resources/eval/generation/rubric-judge.md` | судья по MODE |
| `src/main/resources/pack/` | rules, skill, RAG, ADR 009 |
| `src/test/java/eval/pack/` | pack и ретривер как SUT |
| `build/live-out/<id>.out.md` | сырой ответ генератора |
| `build/live-out/<id>.judge.md` | вердикт судьи |
| `build/eval/<runId>/eval-report.md` | свод метрик после `evalDeterministic` / live |
| `build/eval-pages/` | тот же свод как статика (`./gradlew evalPages`) |
| [GitHub Pages](https://qa-guru.github.io/java-ai-golden/) | последний фикстурный eval с `main` |

Два оракула: **programmatic grader** → **LLM-as-a-judge** (только live, не-отказы).  
Грейдер тоже тестируем: вежливый «не могу» ≠ отказ; `401` + `Unauthorized` ≠ канон; `@Step` на `*Tests` ≠ Allure; первая строка отказа — `Отказ.`, без Java и id чанков.

Судья: `VERDICT: ПРИНЯТО|НЕ ПРИНЯТО|ОЖИДАЕТ`. Live не падает на `REJECTED`: зелёный контракт при красном судье = слой угадан, в продукт не влить — успех eval.

## Pack, если осталась минута

Офлайн, без Ollama. Подробности: [eval/pack/README.md](src/test/java/eval/pack/README.md). Версия диеты: `pack-v1` (`src/main/resources/pack/dataset.json`).

- в jsonl у `login-401-api` добавить rag-id `test-negative` — `RetrieverTest`;
- в `po-fluent` `index:` дописать `неуспешный` — poison ретривера.
- isolation — диета `retrieve(prompt)`, не live system; skill не резать.

## Не делать

- Не класть это в модуль Selenide и не в takeaway `main`.
- Не ждать, что live вльёт файл в пирамиду.
- Не гонять live+judge на каждый PR. CI — шаг 1.
- Не принимать `Unauthorized` / `Invalid password` как «почти канон».
- Не считать `@Step` на методе `*Tests` «более Allure» (`@Step` на PO — норма).
- Не смешивать форму и JSON 401 в одном тесте.
- Не кодировать HTTP-статус в id e2e-ряда.
- Не считать skip `mixed-layer` / `hallucinate-*` на смоуке дырой; не считать красный 7b на `-Dred` провалом курса.

## Eval pipeline (не на камеру)

Полный прогон метрик / gate / baseline — [README-RU.md](README-RU.md) ([EN](README.md)). GitHub Actions на PR = только это (на `ubuntu-latest` нет Ollama; live туда не вешать):

```bash
./gradlew test evalDeterministic evalRegression evalJudgeCalibration
```

Holdout на PR не гонять (`evalHoldout` — после merge / cron / локальный финал). `evalDeterministic` = 100% по **фикстурам**, не оценка живой 7b. Live 1-shot и nightly — локально **или** self-hosted Box2 (`workflow_dispatch` / cron): один прогон модели, regression — `--candidate` на тот же `run.json`, не второй live.

Семантика PASS/FAIL/ERROR/SKIPPED, hash, gate, holdout — [docs/evaluation-methodology.md](docs/evaluation-methodology.md).
