# java-ai-golden

Evaluation mill for **QA.Guru AI agents that write Java autotests**, not for the login app under test.

Repository: [qa-guru/java-ai-golden](https://github.com/qa-guru/java-ai-golden).

This repo is a **file-based evaluation system**:

```
Dataset → Run → Results → Metrics → Baseline → Comparison → Regression → Gate
```

Semantics SSOT: [docs/EVALUATION.md](docs/EVALUATION.md) ([evaluation-methodology.md](docs/evaluation-methodology.md)). Mill walkthrough (camera, ~3 min): [START.md](START.md).

It is not a matrix cell, not a takeaway `main`, and not an MCP server.

## What problem it solves

A green JUnit test on one golden row answers “did this case pass?”.

An eval run answers:

1. How good is this model / pack / grader combo on a **versioned** dataset?
2. Is it **better or worse** than a saved baseline (same dataset, same criteria, same attempt count)?
3. Can CI **block** a regression without mixing that up with “Ollama is down”?

## What is an eval

One **EvalRun** is one execution of the pipeline. Identity:

| Field | Meaning |
|---|---|
| `runId` | Timestamp id, e.g. `2026-09-02T14-32-11` |
| `model` / `judgeModel` | Generator and judge (or off) |
| `datasetVersion` / `datasetHash` | Manifest version + order-independent SHA-256 |
| `configFingerprint` | Stable hash of model+judge+dataset+protocol (not runId/paths) |
| `experimentId` | Optional evaluation target label (`--experiment=prompt-v13`) |
| `gitCommit` | Short SHA |
| cases / attempts | passed, failed, skipped, error (skipped ≠ pass, error ≠ fail) |
| `metrics` | attempt-weighted rates, slices, stability |
| `durationMs` | wall time |

Stored under `build/eval/<runId>/` as `run.json`, `summary.json`, `eval-report.md`, plus per-case artifacts on failure (or `--artifacts=always`).

Do not compare runs with different `datasetVersion`. That is `COMPARISON INVALID`, not a fake percentage.

## Architecture

```
Golden dataset (JSONL + dataset.json)
        ↓
EvalExecutor          modes: deterministic | live | benchmark | regression
        ↓
   ┌────┴─────┬────────────┐
   ▼          ▼            ▼
Contract   Retriever    LLM judge
grader     eval         (soft)
   └────┬─────┴────────────┘
        ▼
    MetricsAggregator     (totalPassed / totalAttempts)
        ▼
    QualityGate           (absolute + optional delta vs baseline)
        ▼
    Reports               JSON + Markdown + console
```

Layers (packages), not a 500-class framework:

| Package | Responsibility |
|---|---|
| `eval.domain` | `EvalRun`, `CaseResult`, `AttemptResult`, rates, statuses |
| `eval.generation` | Golden rows, workflow prompt, mill judge, Ollama adapter |
| `eval.pack` | Lexical retriever + pack diet |
| `eval.grading` | Contract + retrieval graders; hard/soft policy |
| `eval.execution` | Config, executor, repetitions |
| `eval.metrics` | Aggregation |
| `eval.comparison` | Baseline diff, quality gate |
| `eval.reporting` | JSON / Markdown / artifacts |
| `eval.provider` | `ModelRunner` factory: `ollama` (default) or `openai` (OpenAI-compatible HTTP). Cursor agent is a different SUT — not wired. |
| `eval.cli` | `EvalMain`, exit codes |

| Tree | Role |
|---|---|
| `src/main/java` | evaluation application (`EvalMain` on main `runtimeClasspath`) |
| `src/main/resources` | generation dataset, fixtures, holdout, pack diet, judge rubric |
| `src/test/java` | tests of the evaluation application |

Existing mill JUnit tests (`GenerationContractTest`, `LiveGenerationContractTest`, pack tests) stay. Live mill uses the same `ModelRunners` factory as the pipeline. The pipeline **reuses** mill graders; it does not replace them.

## Golden dataset

- File: `src/main/resources/eval/generation/golden-generation.jsonl`
- Version: `src/main/resources/eval/generation/dataset.json` → currently **`generation-v1`**
- Pack version: `src/main/resources/pack/dataset.json` → **`pack-v1`** (EvalRun.packDatasetVersion)
- **Stable case id** = JSONL `id` (e.g. `login-wrong-password-e2e`). Not the line index. Prefixes like `GEN-001` are a naming option for *new* datasets; this mill keeps story ids because fixtures, START, and lab 36 already use them.
- **Holdout** (not for tuning): `src/main/resources/eval/generation/holdout/` → `holdout-v1`. `./gradlew evalHoldout`
- Fixtures: `src/main/resources/eval/generation/fixtures/<id>.out.md` — CI without LLM
- Rules for changing the dataset: [docs/dataset-versioning.md](docs/dataset-versioning.md)
- How to add a case: [docs/adding-a-case.md](docs/adding-a-case.md)

Pack diet (SUT for retriever/isolation/skill): `src/main/resources/pack/`. Changing retrieve sets bumps **`pack-v1`**; changing `expect.rag` / contracts also bumps **`generation-v1`**. Details: `src/test/java/eval/pack/README.md`.

## Contract grading

Hard constraints. No LLM.

`ContractGrader` checks `must_not`, refuse token `Отказ.`, no Java on refuse, RAG header vs **retriever** ids (not jsonl substitution), `@Layer`, class name, status, `contains`, `@Step` on `*Tests`.

JUnit mill tests call the same grader via `ContractAssertions`.

**LLM-as-a-judge is not used** where a string/structure check is enough.

## LLM judge

Soft quality only. Live mill and `--mode=live` (judge on).

Preferred structured result:

```json
{"decision":"ACCEPT|REJECT|PENDING","score":0.92,"reasons":["..."]}
```

Mill still emits `VERDICT: ПРИНЯТО|НЕ ПРИНЯТО|ОЖИДАЕТ`. Parser accepts JSON when schema-valid; otherwise the VERDICT line. Free prose without either → `PENDING`.

**Hard / soft:** if the contract fails, the attempt is `FAIL` even if the judge says `ACCEPT`. Judge ACCEPT cannot override FORBIDDEN. `overallPassRate` is contract (hard). `judgeAcceptRate` is the soft metric. Live mill JUnit still does not fail the test on `REJECTED` (informational); the eval pipeline records it separately.

## Pack / retriever evaluation

Deterministic. `RetrievalGrader` compares `LexicalRetriever.retrieve(prompt)` to `expect.rag`.

Metrics:

- `retrievalPassRate` — set equality vs golden
- `forbiddenRetrieval` — extra ids not in `expect.rag`
- Poisoning: mill `RetrieverTest` still **documents** that a polluted `po-fluent` index steals the form-negative query. That is a pack robustness demo, not an IR `precision@k`. No MRR/recall@k: the retriever has no ranked relevance labels beyond the golden set.

## Metrics

Computed as **hits / total attempts** (or cases for retrieval). Not the unweighted mean of per-case percentages.

| Metric | Meaning | Better |
|---|---|---|
| `overallPassRate` | hard PASS / quality attempts | higher |
| `contractPassRate` | contract pass / graded attempts | higher |
| `judgeAcceptRate` | ACCEPT / (ACCEPT+REJECT); PENDING excluded | higher |
| `retrievalPassRate` | retriever matches `expect.rag` | higher |
| `negativeCasePassRate` | PASS on refuse/red/hallucinate rows | higher |
| `hallucinationRate` | FAIL / attempts on `hallucinate-*` | **lower** |
| `refusalAccuracy` | correct `Отказ.` on refuse rows | higher |
| `layerAccuracy` | `@Layer` present when expected | higher |
| `ragAccuracy` | RAG header matches retriever | higher |

`weightedScore` is **secondary**, equal weights, documented formula in the report, dropped components with `total=0`, **never** the quality gate by itself. Config: `eval.json` → `weights`.

Computed as **hits / total quality attempts** (PASS+FAIL). SKIPPED and ERROR are excluded. Coverage is executed/cases.

A deterministic 100% overall means **fixtures still match the contract**, not “qwen 7b is production-ready”.

## Repeated runs

`--repetitions=N` (live only). Deterministic fixtures always 1 attempt.

Per case: `successRate = passed / qualityAttempts`. Overall still `totalPassed / totalAttempts`.

Example: 5/5, 4/5, 5/5 → generation attempts 14/15 = 93.3%, not (100+80+100)/3.

## Model benchmark

```bash
./gradlew run --args='--mode=benchmark --models=qwen2.5-coder:7b,other:tag --repetitions=5 --red'
```

Same dataset, same graders, same attempt count. Table: Overall / Contract / RAG / Hallucination per model. First two models also get a pairwise comparison JSON.

## Regression detection

```bash
./gradlew evalRegression
```

Requires matching `datasetVersion` (and `datasetHash` when both runs have one). Per-case: `NEW_FAILURE` | `RECOVERED` | `UNCHANGED_PASS` | `UNCHANGED_FAIL` | `NEW_ERROR` (infra, not quality). Metric deltas: `IMPROVED` | `REGRESSED` | `UNCHANGED`. Live delta FAIL if a hard rate drops at all (`allowedRegression = 0`); equal rates are `NO_REGRESSION`.

Paired summary: unchanged pass/fail, regressions, improvements. McNemar is informational, never the sole gate.

Committed baseline `baselines/generation-v1.json` is the **deterministic fixture** snapshot.

Live baseline `baselines/live-generation-v1.json` is a **model** snapshot (non-red rows, 1 attempt). Recaptured from Selectel Box2 Ollama (`qwen2.5-coder:7b`, GHA live smoke). Must include `datasetHash` / `datasetSplit`. Capture:

```bash
./gradlew run --args='--mode=live --judge=true --artifacts=always --save-baseline=baselines/live-generation-v1.json --force-save-baseline'
./gradlew evalLiveRegression
```

Nightly baseline `baselines/nightly-generation-v1.json` is a **different protocol**: all 8 rows including red, 5 attempts. Recaptured from Selectel Box2 Ollama (`qwen2.5-coder:7b`, GHA nightly protocol). Must include `datasetHash` / `datasetSplit`. Do not compare it to the 1-shot live file (`repetitions` / `includeRed` mismatch → `COMPARISON INVALID`). Capture:

```bash
./gradlew evalNightly -DsaveBaseline=baselines/nightly-generation-v1.json -DforceSaveBaseline=true
./gradlew evalNightlyRegression
```

Live and nightly gates use `liveThresholds.allowedRegression` on **hard** rates (overall, contract, retrieval, …). Judge accept rate is reported but **not** in the delta gate (soft, mill: REJECT does not fail live).

Do not treat fixture 100% as live quality. The committed nightly snapshot is **25/40** on `qwen2.5-coder:7b` (red rows `hallucinate-*` and `mixed-layer` failed all 5 attempts; hallucination rate 10/10). That is the 7b picture, not a bug in the gate.

## Providers

Default live provider is **Ollama** (`--provider=ollama`). Mill camera stays that way.

OpenAI-compatible HTTP (LM Studio, vLLM, OpenAI, …):

```bash
./gradlew evalLive -Dprovider=openai -DopenaiBaseUrl=http://127.0.0.1:1234 -Dmodel=...
```

`EvalRun.configuration.provider` is recorded. Cost stays `null` unless the HTTP body actually includes a price (we do not invent USD from tokens). Cursor / Composer is still out of scope.

## Quality gate

`--gate` on **deterministic** runs applies `eval.json` `thresholds` (absolute 100% on fixtures). Fixture eval does not use a live-style regression budget.

`--gate` on **live** runs applies `liveThresholds` only (default: `allowedRegression: 0` — any drop vs the live baseline fails; no absolute mins). `--gate` without a **usable live** baseline (missing file, fixture snapshot, protocol/hash mismatch) **fails the gate** — it is not a skipped-as-PASS. Capture (`evalLive` / `evalNightly`) does not pass `--gate`.

`--save-baseline=PATH` refuses to overwrite an existing file unless `--force-save-baseline` / `-DforceSaveBaseline=true`.

Exit codes (`eval.cli.ExitCode`):

| Code | Meaning |
|---|---|
| 0 | SUCCESS |
| 1 | USAGE (bad args / missing files) |
| 2 | `QUALITY_GATE_FAILED` |
| 3 | `INFRASTRUCTURE_FAILURE` (`MODEL_UNAVAILABLE`, HTTP, empty response, …) |
| 4 | `COMPARISON_INVALID` |

If every attempt is `ERROR` (Ollama down), the process does **not** report 0% AI quality; it exits 3.

Statuses: `PASS` | `FAIL` | `SKIPPED` (red rows without `--red`) | `ERROR` (infra).

`SKIPPED != PASS`. `ERROR != FAIL`. All skipped or empty dataset → exit 2, not 100%.

## CI

GitHub-hosted `ubuntu-latest` has **no Ollama**. Live or nightly on that runner is always `INFRASTRUCTURE_FAILURE`, not a model score. Do **not** install Ollama in the GitHub-hosted job.

Live LLM runs on a **self-hosted** runner (`selectel-java-ai-golden`, labels `ollama` + `java-ai-golden`) on Selectel Box2. Ollama is already on `127.0.0.1:11434` with `qwen2.5-coder:7b` (CPU, not GL10). PR jobs stay on `ubuntu-latest`.

| Where | Command | LLM |
|---|---|---|
| PR ([`ci.yml`](.github/workflows/ci.yml)) `ubuntu-latest` | `./gradlew test evalDeterministic evalRegression evalJudgeCalibration` | no |
| Push to `main` / cron / dispatch (not PR) | `evalHoldout evalHoldoutRegression` | no |
| Live smoke (Box2, `workflow_dispatch`) | `evalLive`, then `evalLiveRegression -Dcandidate=$LATEST/run.json` | yes once, skip red |
| NIGHTLY (Box2, cron 02:00 MSK + dispatch) | `evalNightly` (~30 min CPU), then compare-only `evalNightlyRegression -Dcandidate=…` | yes once, red + 5 reps |
| MAIN live smoke (local Ollama) | same Gradle tasks as live smoke | yes, skip red |
| Holdout (final, not tuning; local) | `./gradlew evalHoldout` then `evalHoldoutRegression` | no |
| Judge calibration (canned) | `./gradlew evalJudgeCalibration` | no |
| Judge calibration (live, local) | `./gradlew evalJudgeCalibrationLive` | yes |

CPU inference is slower than a laptop GPU. Box2 nightly is ~30 min for 40 attempts, not an hour: regression must reuse `build/eval/LATEST/run.json` (`-Dcandidate=`). Do not run the 5-rep protocol twice. Jobs set `OLLAMA_TIMEOUT_MINUTES=10` (mill default remains 3). Do not add a live job without the `ollama` + `java-ai-golden` labels — other Box2 runners (`selectel-niffler` / `book-club` / `realworld`) have no model.

## Reports

| File | Audience |
|---|---|
| `summary.json` | CI / future dashboard |
| `run.json` | Full run without raw model blobs |
| `comparison.json` | Baseline vs candidate |
| `eval-report.md` | Humans |
| `history.jsonl` | Append-only run log under `build/eval/` plus committed `baselines/history.jsonl` |
| `cases/<id>/output.md` | Raw generation (failures by default) |
| `cases/<id>/judge.md` | Raw judge |

Example: [docs/examples/eval-report.md](docs/examples/eval-report.md).

Latency: min / avg / median / p95 / max per run (attempt samples). Tokens: `prompt_eval_count` / `eval_count` from Ollama when present. **Cost is null** unless a provider actually returns a price. Core does not invent USD.

Judge calibration (does not affect production scores or the quality gate): canned `./gradlew evalJudgeCalibration`; live `./gradlew evalJudgeCalibrationLive`. Corpus: `src/main/resources/eval/generation/calibration/judge-calibration.jsonl` (REJECT rows carry a bad `candidate`). Judge consistency: `--judge-repetitions=N` on live.

## How to add a grader

See also [docs/adding-a-grader.md](docs/adding-a-grader.md).

1. Put deterministic checks in `eval.grading` (or extend `ContractGrader`).
2. Return a result object; do not throw from the grader.
3. Unit-test pass, fail, empty, malformed, multiple violations.
4. Never replace a deterministic check with an LLM judge.
5. Keep mill `ContractAssertionsTest` green.

## How to run locally

```bash
git clone https://github.com/qa-guru/java-ai-golden.git
cd java-ai-golden
./gradlew test                          # mill + framework unit tests
./gradlew evalDeterministic             # PR eval + quality gate
./gradlew evalRegression                # vs baselines/generation-v1.json
```

Live (local Ollama, default `qwen2.5-coder:7b`):

```bash
./gradlew test -Dlive=true -DincludeTags=live          # mill JUnit
./gradlew evalLive                                      # pipeline, skip red
./gradlew evalLive -Dred=true                           # include mixed-layer / hallucinate-*
./gradlew evalHoldout                    # holdout-v1 fixtures; do not tune against this
./gradlew evalHoldoutRegression          # vs baselines/holdout-v1.json
./gradlew evalJudgeCalibration           # canned judge confusion matrix; not a mill gate
./gradlew evalJudgeCalibrationLive       # live judge on labeled candidates (local Ollama)
./gradlew evalNightly                                   # 5 reps + red, no gate (capture)
./gradlew evalNightlyRegression                         # delta vs baselines/nightly-generation-v1.json
./gradlew evalLiveRegression                            # delta vs baselines/live-generation-v1.json
```

OpenAI-compatible: `--provider=openai` and `-DopenaiBaseUrl=` / `OPENAI_API_KEY`.

System properties overlay `eval.json`: `model`, `judgeModel`, `judge`, `repetitions`, `red`, `gate`, `outputDir`, `baseline`, `live`, `provider`, `saveBaseline`, `forceSaveBaseline`.

Mill camera flags are unchanged: `-Dlive=true -DincludeTags=live`, `-Dred=true`, `-Djudge=false`, `-DwriteFixtures=true`. Mill live uses the same `ModelRunners` factory as the pipeline (`-Dprovider=openai` works there too). Default remains Ollama.

## Cheap PR eval vs full eval

**PR:** `./gradlew test evalDeterministic evalRegression evalJudgeCalibration` — development fixtures, contract, pack, retriever, grader tests, quality gate vs committed **development** baseline, canned judge calibration. Holdout is **not** on PR.

**Holdout (final):** after merge / cron / dispatch, or locally `./gradlew evalHoldout evalHoldoutRegression`. Do not tune prompt/grader/judge against this split.

**Limited live:** `./gradlew evalLive` — five non-red goldens, one attempt, judge on. Then `evalLiveRegression` against the committed live baseline.

**Full / nightly:** `./gradlew evalNightly` to capture; `evalNightlyRegression` against `baselines/nightly-generation-v1.json`. Same 7b, different protocol (red + 5 reps). Do not raise `liveThresholds.allowedRegression` to paint 7b green.
