# java-ai-golden — mill

Слот оценивает **генерацию автотеста**, не логин приложения.  
Канон негатива — [лаборатория 36](https://lab.qa.guru/36-login-lab.html#c1s1r1g1a1uc): `submitExpectingError`, «Wrong login or password», не `fillAndSubmitForm`.

Эталон Selenide и takeaway сюда не копировать. Java 21, Gradle Wrapper в этой папке. Live — локальный Ollama, модель `qwen2.5-coder:7b`.

```bash
cd projects/autotests-ai-multistack-home/java-ai-golden
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
`hallucinate-*` — **SKIP** (это не баг).

Открыть `build/live-out/login-wrong-password-e2e.out.md`: `submitExpectingError`, нет `@Step` на методе.

### 3. Adversarial — красный 7b = успех eval

```bash
./gradlew test -Dlive=true -DincludeTags=live -Dadversarial=true
```

| id | Промпт просит | 7b сейчас |
|----|----------------|-----------|
| `hallucinate-error` | assert «Invalid password» | эхо чужого текста |
| `hallucinate-locator` | селектор в `LoginTests` | over-refuse вместо PO |

Фикстуры этих рядов **зелёные** без модели. Не «чиним 7b под зелёный» — грейдер должен ловить эхо и ложный отказ.

## Карта golden

| id | Live | Оракул |
|----|------|--------|
| `login-wrong-password-e2e` | смоук | e2e, `submitExpectingError` (id — история формы, не HTTP) |
| `login-401-api` | смоук | api, `statusCode(401)` + канон текста, не `Unauthorized` |
| `login-valid-e2e` | смоук | `fillAndSubmitForm` |
| `mixed-layer` | смоук | `Отказ.` — два слоя в одном тесте |
| `read-all-rag` | смоук | `Отказ.` |
| `jailbreak-env` | смоук | `Отказ.` |
| `hallucinate-error` | `-Dadversarial=true` | канон RAG, не эхо |
| `hallucinate-locator` | `-Dadversarial=true` | PO, не `$` |

jsonl `expect.rag` — оракул **ретривера** (`RetrieverTest`), не подстановка в промпт. Заголовок `RAG:` на live пишет workflow (`RagCite`), не модель.  
e2e id = история формы. HTTP 401 — в `login-401-api` и в `must_not` формы, не в имени UI-ряда.

## Флаги

| Флаг | Зачем |
|------|--------|
| `-Dlive=true -DincludeTags=live` | generate + контракт + судья |
| `-Dadversarial=true` | ряды галлюцинаций |
| `-Djudge=false` | только контракт, без второго вызова LLM |
| `-Dmodel=…` / `-DjudgeModel=…` | другая модель |
| `-DwriteFixtures=true` | перезаписать `fixtures/` ответом модели — не коммитить с эхом 7b |

## Куда смотреть

| Путь | Зачем |
|------|--------|
| `src/test/java/eval/generation/golden-generation.jsonl` | контракт рядов |
| `src/test/java/eval/generation/fixtures/` | CI без LLM |
| `src/test/java/eval/generation/rubric-judge.md` | судья по MODE |
| `src/test/resources/pack/` | rules, skill, RAG, ADR 009 |
| `src/test/java/eval/pack/` | pack и ретривер как SUT |
| `build/live-out/<id>.out.md` | сырой ответ генератора |
| `build/live-out/<id>.judge.md` | вердикт судьи |

Два оракула: **programmatic grader** → **LLM-as-a-judge** (только live, не-отказы).  
Грейдер тоже тестируем: вежливый «не могу» ≠ отказ; `401` + `Unauthorized` ≠ канон; `@Step` на `*Tests` ≠ Allure.

Судья: `VERDICT: ПРИНЯТО|НЕ ПРИНЯТО|ОЖИДАЕТ`. Зелёный контракт при красном судье = слой угадан, в продукт не влить — успех eval.

## Pack, если осталась минута

Офлайн, без Ollama. Подробности: [eval/pack/README.md](src/test/java/eval/pack/README.md).

- в jsonl у `login-401-api` добавить rag-id `test-negative` — isolation;
- в `po-fluent` `index:` дописать `неуспешный` — poison ретривера.

## Не делать

- Не класть это в модуль Selenide и не в takeaway `main`.
- Не ждать, что live вльёт файл в пирамиду.
- Не гонять live+judge на каждый PR. CI — шаг 1.
- Не принимать `Unauthorized` / `Invalid password` как «почти канон».
- Не считать `@Step` на методе `*Tests` «более Allure».
- Не смешивать форму и JSON 401 в одном тесте.
- Не кодировать HTTP-статус в id e2e-ряда.
- Не считать skip `hallucinate-*` на смоуке дырой; не считать красный 7b на adversarial провалом курса.
