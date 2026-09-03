# java-ai-golden

Стенд для оценки AI-агентов, которые пишут Java-автотесты. Под тестом здесь **агент** — модель + промпт + RAG-пак, — а не логин-приложение из примеров.

[English version](README.md) · [демо мельницы на 3 минуты](START.md) · [методология (SSOT)](docs/evaluation-methodology.md)

## Что он делает

Прогоняет версионированный golden-датасет через модель, грейдит ответы детерминированными проверками (плюс опциональный LLM-судья), сводит это в метрики, сравнивает с сохранённым baseline и даёт CI упасть на регрессии — не путая «модель стала хуже» с «Ollama лежала».

```
Датасет → Прогон → Результаты кейсов → Метрики → Сравнение с baseline → Quality gate
```

Зелёный JUnit-тест говорит, что прошёл один кейс. Eval-прогон говорит, насколько хороша связка модель/пак/промпт на версионированном датасете, лучше это или хуже прошлого раза и должен ли CI заблокировать изменение.

Это не ячейка матрицы, не takeaway студента и не MCP-сервер.

## Быстрый старт

Java 21, Gradle Wrapper лежит в репозитории. Модель ни для чего из этого не нужна:

```bash
git clone https://github.com/qa-guru/java-ai-golden.git
cd java-ai-golden
./gradlew test               # юнит-тесты + фикстуры мельницы
./gradlew evalDeterministic  # eval по фикстурам + quality gate
./gradlew evalRegression     # diff против baselines/generation-v1.json
```

С локальной Ollama (модель по умолчанию `qwen2.5-coder:7b`):

```bash
./gradlew evalLive            # 5 не-красных goldens, одна попытка, судья включён
./gradlew evalLiveRegression  # дельта против закоммиченного live-baseline
```

Результаты пишутся в `build/eval/<runId>/`: `run.json`, `summary.json`, `eval-report.md` и по-кейсовые артефакты для падений (`--artifacts=always` — для всех). Пример отчёта: [docs/examples/eval-report.md](docs/examples/eval-report.md).

## Датасет

Восемь development-кейсов в `src/main/resources/eval/generation/golden-generation.jsonl`, версия `generation-v1`. Пять обычных, три «красных», где 7b сейчас закономерно падает (`mixed-layer`, `hallucinate-error`, `hallucinate-locator`) — на смоуке они skip, включаются флагом `--red`.

| Путь | Что внутри |
|---|---|
| `eval/generation/golden-generation.jsonl` | Промпты кейсов, ожидания, ограничения `must_not` |
| `eval/generation/dataset.json` | Версия датасета + changelog |
| `eval/generation/fixtures/<id>.out.md` | Записанные ответы, чтобы CI грейдил без LLM |
| `eval/generation/holdout/` | Финальный сплит `holdout-v1` — под него не тюнить |
| `eval/generation/rubric-judge.md` | Рубрика судьи |
| `pack/` | Сам пак под тестом: rules, skill, чанки, ADR 009 (`pack-v1`) |

Идентичность кейса — это `id` из JSONL (`login-wrong-password-e2e`), а не номер строки. Изменил промпт или ожидания — бампни версию: [dataset-versioning.md](docs/dataset-versioning.md). Как добавить ряд: [adding-a-case.md](docs/adding-a-case.md).

## Как грейдятся ответы

| Грейдер | Тип | Что проверяет |
|---|---|---|
| `ContractGrader` | жёсткий, без LLM | Строки `must_not`, токен отказа `Отказ.` и отсутствие Java после него, заголовок `RAG:` против реальной выдачи ретривера, `@Layer`, имя класса, HTTP-статус, обязательные подстроки, `@Step` не на своём месте в `*Tests` |
| `RetrievalGrader` | жёсткий, без LLM | `LexicalRetriever.retrieve(prompt)` как множество против оракула `expect.rag` |
| LLM-судья | мягкий, только live | Ясность, полнота, стиль — репортится, но не гейтит |

Контракт главнее. `ACCEPT` судьи не спасает провал контракта, `REJECT` судьи не топит пройденный контракт. Всё, что ловится проверкой строки или структуры, судье не отдаётся.

Судья предпочитает структурированный ответ — `{"decision":"ACCEPT|REJECT|PENDING","score":0.92,"reasons":["..."]}` — и откатывается на строку мельницы `VERDICT: ПРИНЯТО|НЕ ПРИНЯТО|ОЖИДАЕТ`. Свободная проза без того и другого — `PENDING`, он исключён из `judgeAcceptRate`. Качество самого судьи меряется отдельно: `./gradlew evalJudgeCalibration` (на консервах) и `evalJudgeCalibrationLive`; калибровка не влияет на продовые оценки и gate.

## Метрики

Всё считается **по попыткам**: попадания делятся на общее число quality-попыток (`PASS` + `FAIL`), а не усредняются по кейсам. `SKIPPED` и `ERROR` в знаменатель не попадают. Кейс, который прошёл с одной попытки, рядом с кейсом, который упал пять раз из пяти, даёт 1/6 = 17%, а не 50%, которые получились бы усреднением процентов по кейсам.

| Метрика | Смысл | Лучше |
|---|---|---|
| `overallPassRate` | Жёсткий `PASS` / quality-попытки | выше |
| `contractPassRate` | Контракт пройден / оценённые попытки | выше |
| `retrievalPassRate` | Выдача ретривера совпала с `expect.rag` | выше |
| `judgeAcceptRate` | `ACCEPT` / (`ACCEPT`+`REJECT`), мягкая | выше |
| `negativeCasePassRate` | Пройдены ряды на отказ / красные / галлюцинации | выше |
| `hallucinationRate` | Падения на `hallucinate-*` | **ниже** |
| `refusalAccuracy`, `layerAccuracy`, `ragAccuracy` | Корректный отказ, слой, заголовок RAG | выше |

`weightedScore` — вспомогательное число с равными весами (конфиг: `eval.json` → `weights`), само по себе гейтом не бывает. Стабильность по кейсам, срезы и полные определения — в [evaluation-methodology.md](docs/evaluation-methodology.md).

100% по фикстурам значит «фикстуры всё ещё соответствуют контракту». Это не утверждение о качестве живой модели.

## Baseline, регрессии, gate

Четыре закоммиченных снимка, все на `generation-v1` и **не** взаимозаменяемые:

| Файл | Протокол |
|---|---|
| `baselines/generation-v1.json` | фикстуры, 1 попытка, без LLM |
| `baselines/holdout-v1.json` | holdout-фикстуры, 1 попытка, без LLM |
| `baselines/live-generation-v1.json` | модель, красные пропущены, 1 попытка |
| `baselines/nightly-generation-v1.json` | модель, красные включены, 5 попыток |

Для сравнения должны совпасть `datasetVersion`, `datasetHash`, хеш пака, режим, `repetitions` и `includeRed`. Расхождение — это `COMPARISON_INVALID` (код 4), а не выдуманный процент. По кейсам получаешь `NEW_FAILURE` / `RECOVERED` / `UNCHANGED_PASS` / `UNCHANGED_FAIL` / `NEW_ERROR`; McNemar информационный и один гейтом не бывает.

Gate бывает двух видов. На детерминированных прогонах работают абсолютные `thresholds` из `eval.json` (100% по фикстурам, 0% галлюцинаций). На live-прогонах работает `liveThresholds.allowedRegression = 0` против live-baseline: любое падение жёсткой метрики — fail, равенство — pass, а отсутствующий или несовпадающий по протоколу baseline **валит** gate, а не проскакивает молча. `judgeAcceptRate` репортится, но не гейтится.

Перезапись baseline (у capture-прогонов нет `--gate`, а `--save-baseline` не перетирает файл без `--force-save-baseline`):

```bash
./gradlew run --args='--mode=live --judge=true --artifacts=always \
  --save-baseline=baselines/live-generation-v1.json --force-save-baseline'
./gradlew evalNightly -DsaveBaseline=baselines/nightly-generation-v1.json -DforceSaveBaseline=true
```

Для ориентира: закоммиченный nightly-снимок — **25/40** на `qwen2.5-coder:7b`, красные ряды падают все пять попыток, hallucination rate 10/10. Так выглядит 7b, а не сломанный gate.

Коды выхода (`eval.cli.ExitCode`): `0` успех · `1` usage · `2` gate не пройден (в том числе пустой или полностью пропущенный прогон) · `3` инфраструктура · `4` сравнение невалидно. Если все попытки упали из-за лежащей Ollama, процесс выходит с 3, а не рапортует 0% качества модели.

## CI

На GitHub-раннере `ubuntu-latest` нет Ollama, поэтому live там всегда был бы инфраструктурным падением. Live крутится на self-hosted раннере Selectel Box2 (`selectel-java-ai-golden`, лейблы `ollama` + `java-ai-golden`), где Ollama уже отдаёт `qwen2.5-coder:7b` на `127.0.0.1:11434` с CPU.

| Триггер | Что гоняет | LLM |
|---|---|---|
| Pull request ([`ci.yml`](.github/workflows/ci.yml)) | `test evalDeterministic evalRegression evalJudgeCalibration` | нет |
| Push в `main`, cron, dispatch | то же плюс `evalHoldout evalHoldoutRegression` | нет |
| Live-смоук (Box2, dispatch) | `evalLive`, затем `evalLiveRegression -Dcandidate=$LATEST/run.json` | да, красные пропущены |
| Nightly (Box2, cron 02:00 MSK) | `evalNightly` (~30 мин на CPU), затем regression только на сравнение | да, красные + 5 повторов |

Gradle-таски holdout убраны с pull request, чтобы под финальный сплит никто не тюнил, но сломанная holdout-фикстура PR всё равно валит: `./gradlew test` гоняет `HoldoutDatasetTest` (executor + гейт vs `baselines/holdout-v1.json` и тот же CLI, что `evalHoldoutRegression`). Это гейт на контракт файлов, а не holdout-оценка в артефактах PR.

CPU-инференс медленный, поэтому шаг regression переиспользует `build/eval/LATEST/run.json` через `-Dcandidate=` вместо второго прогона модели. Live-джобы ставят `OLLAMA_TIMEOUT_MINUTES=10` (дефолт мельницы — 3) и обязаны нести оба лейбла раннера: на остальных раннерах Box2 модели нет.

## Структура

| Путь | Роль |
|---|---|
| `src/main/java/eval/` | Приложение оценки: `domain`, `dataset`, `generation`, `pack`, `grading`, `execution`, `metrics`, `comparison`, `reporting`, `provider`, `cli` |
| `src/main/resources/` | Датасет, фикстуры, holdout, диета пака, рубрика судьи |
| `src/test/java/eval/` | Тесты самого стенда плюс JUnit-демо мельницы (`GenerationContractTest`, `LiveGenerationContractTest`, pack-тесты) |
| `baselines/` | Закоммиченные снимки + append-only `history.jsonl` |
| `eval.json` | Конфиг по умолчанию, пороги, веса |

Пайплайн переиспользует грейдеры мельницы через `ContractAssertions`, а не реализует их заново; фабрика провайдеров `ModelRunners` тоже общая.

## Конфигурация

Любое поле `eval.json` перекрывается системным свойством: `model`, `judgeModel`, `judge`, `repetitions`, `red`, `gate`, `outputDir`, `baseline`, `live`, `provider`, `saveBaseline`, `forceSaveBaseline`.

Провайдер по умолчанию — Ollama. Любой OpenAI-совместимый HTTP (LM Studio, vLLM, OpenAI) тоже работает:

```bash
./gradlew evalLive -Dprovider=openai -DopenaiBaseUrl=http://127.0.0.1:1234 -Dmodel=...
```

Провайдер записывается в `EvalRun.configuration.provider`. Стоимость остаётся `null`, пока провайдер реально не вернёт цену: токены в выдуманные доллары не пересчитываются. Cursor / Composer — другой SUT, он сюда не подключён.

Сравнение нескольких моделей на одном датасете, грейдерах и числе попыток:

```bash
./gradlew run --args='--mode=benchmark --models=qwen2.5-coder:7b,other:tag --repetitions=5 --red'
```

## Чего не делать

- Не сравнивать прогоны с разными версиями датасета и фикстурный baseline с live-прогоном.
- Не поднимать `liveThresholds.allowedRegression`, чтобы покрасить 7b в зелёный: красные ряды существуют именно чтобы показывать её падения.
- Не тюнить промпт, грейдер и судью под holdout-сплит.
- Не заменять детерминированную проверку вызовом судьи.
- Не считать `SKIPPED` за pass, а `ERROR` за fail.
- Не ставить Ollama в GitHub-hosted джобу и не заводить live-джобу без лейблов раннера Box2.
- Не коммитить перезаписанные фикстуры (`-DwriteFixtures=true`), не прочитав, что там на самом деле написала модель.

## Документация

- [START.md](START.md) — прогон мельницы на 3 минуты
- [docs/evaluation-methodology.md](docs/evaluation-methodology.md) — SSOT по статусам, метрикам, сравнимости и gate
- [docs/dataset-versioning.md](docs/dataset-versioning.md) — когда и как бампать версию
- [docs/adding-a-case.md](docs/adding-a-case.md) · [docs/adding-a-grader.md](docs/adding-a-grader.md)
- [src/test/java/eval/pack/README.md](src/test/java/eval/pack/README.md) — пак и ретривер как SUT
