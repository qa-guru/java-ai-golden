# С чего начать

Слот оценивает **генерацию автотестов** (AI-first workflow), не логин приложения.  
Канон негатива — [лаборатория 36](https://lab.qa.guru/36-login-lab.html#c1s1r1g1a1uc): `submitExpectingError`, текст «Wrong login or password», не `fillAndSubmitForm`.

Эталон Selenide и takeaway сюда не копировать.

Нужны: Java 21, Gradle Wrapper в этой папке, для live — локальный Ollama и модель `qwen2.5-coder:7b`.

## 1. Открыть проект

Корень: `projects/autotests-ai-multistack-home/java-ai-golden/`

| Файл | Зачем |
|------|--------|
| `src/test/java/eval/generation/golden-generation.jsonl` | контракт: слой, `contains`, `must_not`, RAG-id |
| `src/test/java/eval/generation/fixtures/` | записанный эталон (CI) |
| `src/test/java/eval/generation/rubric-judge.md` | спека LLM-as-a-judge, по MODE |
| `src/test/java/eval/generation/ContractAssertionsTest.java` | регрессии грейдера: false green, которые уже ловили live |
| `src/test/java/eval/pack/` | pack как SUT: диета, изоляция слоёв, лексический ретривер |
| `src/test/resources/pack/` | диета: rules, skill, RAG, ADR 009, контекст PO |

Два оракула: **programmatic grader** (`ContractAssertions`) → **LLM-as-a-judge** (`Judge`, только live).  
Грейдер тоже тестируем: вежливый «не могу» ≠ отказ; Java канон при обрезанной строке `RAG:` ≠ зелёный; `401` + `Unauthorized` ≠ канон.

## 2. Сначала без модели

```bash
cd projects/autotests-ai-multistack-home/java-ai-golden
./gradlew test
```

Ожидание: `GenerationContractTest` + регрессии грейдера + `eval.pack`, зелёные.

Красный демо: в `fixtures/login-401-ui.out.md` повесить `@Step` на метод теста — упадёт `must_not`. Цепочка `fillAndSubmitForm` на негативе — тоже.

## 3. Полный цикл: generate → contract → judge

```bash
./gradlew test -Dlive=true -DincludeTags=live
```

Ожидание: generate + дискретная сверка; для не-отказов ещё вызов судьи. ~1 мин.

Лог:

- stdout `===== RETRIEVE <id> =====` / `===== LIVE <id> =====` / `===== JUDGE <id> =====`
- `build/live-out/<id>.out.md` — ответ генератора
- `build/live-out/<id>.judge.md` — вердикт судьи

Судью выключить: `-Djudge=false`. Другая модель судьи: `-DjudgeModel=…`.

Отказы (`read-all-rag`, `jailbreak-env`) — только контракт, без judge. Первая строка отказа: `Отказ.`

## 4. Как читать результат

Контракт (без LLM): `@Layer`, класс, **все** RAG-id, `contains`, `must_not`.  
Для API ещё канон тела: `Wrong login or password`, не `Unauthorized`.

Судья смотрит MODE (`form-negative` / `form-happy` / `api-negative`): happy path не штрафуют за `fillAndSubmitForm`. Первая строка `VERDICT: ПРИНЯТО|НЕ ПРИНЯТО|ОЖИДАЕТ`.

Зелёный контракт при красном судье = слой угадан, в продукт не влить — успех eval, не баг JUnit.

Live красный при каноничном Java — смотри цитирование RAG (подмножество id), токен отказа и `@Step` на методе теста. Это не «модель плохая», это дырявый контракт; такие дыры фиксируем в `ContractAssertionsTest` и в `eval.pack`.

Ретривер должен совпадать со слоем: в API-кейсе не кладём `test-negative` (там сниппет формы). Live **не** читает `expect.rag` — jsonl это оракул для `RetrieverTest`. Шаги Allure — на PO, не `@Step` в `*Tests`.

## 5. Две минуты на занятии

1. `./gradlew test` — golden + pack без LLM.
2. Live — `login-401-ui.out.md`: `submitExpectingError`, все четыре RAG-id, **нет** `@Step` на методе.
3. Если live красный — читать assertion message (полный stack в Gradle) и судью. False green больше не цель.

## 6. Pack как продукт

Офлайн, без Ollama. Ломает изоляцию: дописать `test-negative` в rag у `login-401-api`.  
Ломает ретривер: в `index:` у `po-fluent` дописать «неуспешный».

Подробности: `src/test/java/eval/pack/README.md`.

## Не делать

- Не класть это в модуль Selenide и не в takeaway `main`.
- Не ждать, что live вльёт файл в пирамиду.
- Не гонять live+judge на каждый PR. CI — шаг 2.
- Не принимать `Unauthorized` / `Invalid password` как «почти канон».
- Не считать `@Step` на методе `*Tests` «более Allure».
