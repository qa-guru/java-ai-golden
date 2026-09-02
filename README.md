# java-ai-golden

Evaluation mill for **QA.Guru AI agents that write Java autotests**, not for the login app under test.

Repository: [qa-guru/java-ai-golden](https://github.com/qa-guru/java-ai-golden).

This repo is a **file-based evaluation system**: golden dataset → execution → deterministic grading → optional LLM judge → metrics → repeated runs → baseline comparison → regression / quality gate → JSON+Markdown reports.

It is not a matrix cell, not a takeaway `main`, and not an MCP server.

Mill walkthrough (camera, ~3 min): [START.md](START.md). Semantics SSOT: [docs/evaluation-methodology.md](docs/evaluation-methodology.md).

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
| `model` | Generator model (unused in deterministic mode) |
| `judgeModel` | Judge model, or off |
| `datasetVersion` | e.g. `generation-v1` |
| `gitCommit` | Short SHA |
| `configuration` | mode, repetitions, red flag, artifacts |
| cases / attempts | totals, passed, failed, skipped, error |
| `metrics` | component rates, never a single unexplained score |
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
| `eval.provider` | `ModelRunner` — core does not import OpenAI/Anthropic/Gemini |
| `eval.cli` | `EvalMain`, exit codes |

Existing mill JUnit tests (`GenerationContractTest`, `LiveGenerationContractTest`, pack tests) stay. The pipeline **reuses** their graders; it does not replace them.

## Golden dataset

- File: `src/test/java/eval/generation/golden-generation.jsonl`
- Version: `src/test/java/eval/generation/dataset.json` → currently **`generation-v1`**
- **Stable case id** = JSONL `id` (e.g. `login-wrong-password-e2e`). Not the line index. Prefixes like `GEN-001` are a naming option for *new* datasets; this mill keeps story ids because fixtures, START, and lab 36 already use them.
- Fixtures: `src/test/java/eval/generation/fixtures/<id>.out.md` — CI without LLM
- Rules for changing the dataset: [docs/dataset-versioning.md](docs/dataset-versioning.md)
- How to add a case: [docs/adding-a-case.md](docs/adding-a-case.md)

Pack diet (SUT for retriever/isolation/skill): `src/test/resources/pack/`. Changing `expect.rag` or retriever behaviour is a **dataset change** — bump `generation-v1`. Details: `src/test/java/eval/pack/README.md`.

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

A deterministic 100% overall means **fixtures still match the contract**, not “qwen 7b is production-ready”.

## Repeated runs

`--repetitions=N` (live only). Deterministic fixtures always 1 attempt.

Per case: `successRate = passed / qualityAttempts`. Overall still `totalPassed / totalAttempts`.

Example: 5/5, 4/5, 5/5 → generation attempts 14/15 = 93.3%, not (100+80+100)/3.

## Model benchmark

```bash
./gradlew evalLive --args='--mode=benchmark --models=qwen2.5-coder:7b,other:tag --repetitions=5 --red'
```

Same dataset, same graders, same attempt count. Table: Overall / Contract / RAG / Hallucination per model. First two models also get a pairwise comparison JSON.

## Regression detection

```bash
./gradlew evalRegression
# or
./gradlew evalDeterministic --args='--mode=regression --baseline=baselines/generation-v1.json --gate'
```

Requires matching `datasetVersion`. Per-case: `NEW_FAILURE` | `RECOVERED` | `STILL_FAILING` | `STILL_PASSING`. Metric deltas: `IMPROVED` | `REGRESSED` | `UNCHANGED`.

Committed baseline `baselines/generation-v1.json` is the **deterministic fixture** snapshot. A live-model baseline is a different file you save with `--save-baseline=…` after a live run; do not treat fixture 100% as live quality.

## Quality gate

`--gate` applies `eval.json` `thresholds`:

- Absolute mins for pass rates; **max** for `hallucinationRate`
- Optional `allowedRegression` (delta): candidate ≥ baseline − δ (inverted for hallucination)

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

## CI

| Job | Command | LLM |
|---|---|---|
| PR | `./gradlew test` then `./gradlew evalDeterministic` then `./gradlew evalRegression` | no |
| Merge / limited live | `./gradlew evalLive` (self-hosted or laptop with Ollama) | yes, skips `red` rows |
| Nightly | `./gradlew evalNightly` (`--red --repetitions=5`) | yes, full |

Do not run full live+judge on every PR.

GitHub: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — PR path only. Live/nightly are `workflow_dispatch` (need Ollama).

## Reports

| File | Audience |
|---|---|
| `summary.json` | CI / future dashboard |
| `run.json` | Full run without raw model blobs |
| `comparison.json` | Baseline vs candidate |
| `eval-report.md` | Humans |
| `cases/<id>/output.md` | Raw generation (failures by default) |
| `cases/<id>/judge.md` | Raw judge |

Example: [docs/examples/eval-report.md](docs/examples/eval-report.md).

Latency: min / avg / median / p95 / max per run (attempt samples). Tokens: `prompt_eval_count` / `eval_count` from Ollama when present. **Cost is null** unless a provider actually returns a price. Core does not invent USD.

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
./gradlew evalNightly                                   # 5 reps + red
./gradlew evalLive --args='--save-baseline=baselines/live-generation-v1.json'
```

System properties overlay `eval.json`: `model`, `judgeModel`, `judge`, `repetitions`, `red`, `gate`, `outputDir`, `baseline`, `live`.

Mill camera flags are unchanged: `-Dlive=true -DincludeTags=live`, `-Dred=true`, `-Djudge=false`, `-DwriteFixtures=true`.

## Cheap PR eval vs full eval

**PR:** `./gradlew test evalDeterministic evalRegression` — fixtures, contract, pack, retriever, grader tests, quality gate vs committed deterministic baseline.

**Limited live:** `./gradlew evalLive` — five non-red goldens, one attempt, judge on.

**Full / nightly:** `./gradlew evalNightly` — all rows including red, `repetitions=5`, artifacts always. Add `--gate` only against a **live** baseline you trust; do not lower thresholds to paint 7b green.
