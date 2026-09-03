# java-ai-golden

An evaluation harness for AI agents that write Java autotests. The system under test is the **agent** — model + prompt + RAG pack — not the login application used in the examples.

[Русская версия](README-RU.md) · [3-minute mill demo](START.md) · [methodology SSOT](docs/evaluation-methodology.md)

## What it does

Runs a versioned golden dataset through a model, grades the answers with deterministic checks (plus an optional LLM judge), turns that into metrics, compares them with a saved baseline, and lets CI fail on a regression — without confusing “the model got worse” with “Ollama was down”.

```
Dataset → Run → Case results → Metrics → Baseline comparison → Quality gate
```

A green JUnit test tells you one case passed. An eval run tells you how good a model/pack/prompt combination is on a versioned dataset, whether it is better or worse than last time, and whether CI should block the change.

This is not a matrix cell, not the student takeaway, and not an MCP server.

## Quick start

Java 21; the Gradle wrapper is in the repo. Nothing here needs a model:

```bash
git clone https://github.com/qa-guru/java-ai-golden.git
cd java-ai-golden
./gradlew test               # unit tests + mill fixtures
./gradlew evalDeterministic  # eval on fixtures + quality gate
./gradlew evalRegression     # diff vs baselines/generation-v1.json
```

With a local Ollama (default model `qwen2.5-coder:7b`):

```bash
./gradlew evalLive            # 5 non-red goldens, 1 attempt, judge on
./gradlew evalLiveRegression  # delta vs the committed live baseline
```

Results land in `build/eval/<runId>/`: `run.json`, `summary.json`, `eval-report.md`, and per-case artifacts for failures (`--artifacts=always` for all of them). Sample report: [docs/examples/eval-report.md](docs/examples/eval-report.md).

## Dataset

Eight development cases in `src/main/resources/eval/generation/golden-generation.jsonl`, version `generation-v1`. Five are normal rows, three are “red” rows where 7b is currently expected to fail (`mixed-layer`, `hallucinate-error`, `hallucinate-locator`) and are skipped unless you pass `--red`.

| Path | What it holds |
|---|---|
| `eval/generation/golden-generation.jsonl` | Case prompts, expectations, `must_not` constraints |
| `eval/generation/dataset.json` | Dataset version + changelog |
| `eval/generation/fixtures/<id>.out.md` | Recorded answers, so CI can grade without an LLM |
| `eval/generation/holdout/` | Final split, `holdout-v1` — never tune against it |
| `eval/generation/rubric-judge.md` | Judge rubric |
| `pack/` | The RAG pack under test: rules, skill, chunks, ADR 009 (`pack-v1`) |

Case identity is the JSONL `id` (`login-wrong-password-e2e`), never the line number. Changing prompts or expectations means bumping the version — see [dataset-versioning.md](docs/dataset-versioning.md); adding a row is [adding-a-case.md](docs/adding-a-case.md).

## How answers are graded

| Grader | Kind | Checks |
|---|---|---|
| `ContractGrader` | hard, no LLM | `must_not` strings, refusal token `Отказ.` with no Java after it, `RAG:` header against what the retriever actually returned, `@Layer`, class name, HTTP status, required substrings, `@Step` misuse on `*Tests` |
| `RetrievalGrader` | hard, no LLM | `LexicalRetriever.retrieve(prompt)` as a set vs the `expect.rag` oracle |
| LLM judge | soft, live only | Clarity, completeness, style — reported, never a gate |

The contract wins. A judge `ACCEPT` cannot rescue a contract `FAIL`, and a judge `REJECT` cannot sink a contract `PASS`. Anything a string or structure check can catch is never delegated to the judge.

The judge prefers structured output — `{"decision":"ACCEPT|REJECT|PENDING","score":0.92,"reasons":["..."]}` — and falls back to the mill’s `VERDICT: ПРИНЯТО|НЕ ПРИНЯТО|ОЖИДАЕТ` line. Free prose with neither is `PENDING`, which is excluded from `judgeAcceptRate`. Judge quality itself is measured separately by `./gradlew evalJudgeCalibration` (canned) and `evalJudgeCalibrationLive`; calibration never touches production scores.

## Metrics

Everything is **attempt-weighted**: hits divided by total quality attempts (`PASS` + `FAIL`), never the mean of per-case percentages. `SKIPPED` and `ERROR` stay out of the denominator. A case that ran once and passed, next to a case that ran five times and always failed, is 1/6 = 17% — not the 50% you would get by averaging the two case rates.

| Metric | Meaning | Better |
|---|---|---|
| `overallPassRate` | Hard `PASS` / quality attempts | higher |
| `contractPassRate` | Contract pass / graded attempts | higher |
| `retrievalPassRate` | Retriever set matches `expect.rag` | higher |
| `judgeAcceptRate` | `ACCEPT` / (`ACCEPT`+`REJECT`), soft | higher |
| `negativeCasePassRate` | Pass on refuse / red / hallucinate rows | higher |
| `hallucinationRate` | Fails on `hallucinate-*` | **lower** |
| `refusalAccuracy`, `layerAccuracy`, `ragAccuracy` | Correct refusal, layer, RAG header | higher |

`weightedScore` is a secondary convenience number with equal weights (config: `eval.json` → `weights`) and is never the gate on its own. Per-case stability, slices, and the full definitions live in [evaluation-methodology.md](docs/evaluation-methodology.md).

100% on fixtures means the fixtures still satisfy the contract. It is not a statement about live model quality.

## Baselines, regression, gate

Four committed snapshots, all on `generation-v1` and **not** interchangeable:

| File | Protocol |
|---|---|
| `baselines/generation-v1.json` | fixtures, 1 attempt, no LLM |
| `baselines/holdout-v1.json` | holdout fixtures, 1 attempt, no LLM |
| `baselines/live-generation-v1.json` | model, skip red, 1 attempt |
| `baselines/nightly-generation-v1.json` | model, red rows included, 5 attempts |

Comparison requires the same `datasetVersion`, `datasetHash`, pack hash, mode, `repetitions`, and `includeRed`. A mismatch is `COMPARISON_INVALID` (exit 4), not an invented percentage. Per case you get `NEW_FAILURE` / `RECOVERED` / `UNCHANGED_PASS` / `UNCHANGED_FAIL` / `NEW_ERROR`; the McNemar figure is informational and never the sole gate.

The gate has two flavours. On deterministic runs it applies the absolute `thresholds` from `eval.json` (100% on fixtures, 0% hallucination). On live runs it applies `liveThresholds.allowedRegression = 0` against a live baseline: any drop in a hard rate fails, equal rates pass, and a missing or protocol-mismatched baseline **fails** rather than silently passing. Judge accept rate is reported but not gated.

Re-capturing a baseline (capture runs never pass `--gate`, and `--save-baseline` refuses to overwrite without `--force-save-baseline`):

```bash
./gradlew run --args='--mode=live --judge=true --artifacts=always \
  --save-baseline=baselines/live-generation-v1.json --force-save-baseline'
./gradlew evalNightly -DsaveBaseline=baselines/nightly-generation-v1.json -DforceSaveBaseline=true
```

For reference, the committed nightly snapshot is **25/40** on `qwen2.5-coder:7b`: the red rows fail all five attempts, hallucination rate 10/10. That is what a 7b model looks like, not a broken gate.

Exit codes (`eval.cli.ExitCode`): `0` success · `1` usage · `2` quality gate failed (including an empty or fully skipped run) · `3` infrastructure failure · `4` comparison invalid. If every attempt errors because Ollama is down, the run exits 3 instead of reporting 0% model quality.

## CI

GitHub-hosted `ubuntu-latest` has no Ollama, so live jobs there would always be infrastructure failures. Live work runs on the self-hosted Selectel Box2 runner (`selectel-java-ai-golden`, labels `ollama` + `java-ai-golden`), where Ollama already serves `qwen2.5-coder:7b` on `127.0.0.1:11434` from CPU.

| Trigger | Runs | LLM |
|---|---|---|
| Pull request ([`ci.yml`](.github/workflows/ci.yml)) | `test evalDeterministic evalRegression evalJudgeCalibration` | no |
| Push to `main`, cron, dispatch | the same plus `evalHoldout evalHoldoutRegression` | no |
| Live smoke (Box2, dispatch) | `evalLive`, then `evalLiveRegression -Dcandidate=$LATEST/run.json` | yes, red skipped |
| Nightly (Box2, cron 02:00 MSK) | `evalNightly` (~30 min on CPU), then a compare-only regression | yes, red + 5 reps |

The holdout Gradle tasks are kept off pull requests so nobody tunes against the final split, but a broken holdout fixture still fails the PR: `./gradlew test` runs `HoldoutDatasetTest`, which is a file-contract gate rather than a holdout score in the PR artifacts.

Because CPU inference is slow, the regression step reuses `build/eval/LATEST/run.json` via `-Dcandidate=` instead of running the model twice. Live jobs set `OLLAMA_TIMEOUT_MINUTES=10` (the mill default is 3) and must carry both runner labels — the other Box2 runners have no model installed.

## Layout

| Path | Role |
|---|---|
| `src/main/java/eval/` | The evaluation application: `domain`, `dataset`, `generation`, `pack`, `grading`, `execution`, `metrics`, `comparison`, `reporting`, `provider`, `cli` |
| `src/main/resources/` | Dataset, fixtures, holdout, pack diet, judge rubric |
| `src/test/java/eval/` | Tests of the harness, plus the mill JUnit demo (`GenerationContractTest`, `LiveGenerationContractTest`, pack tests) |
| `baselines/` | Committed snapshots + append-only `history.jsonl` |
| `eval.json` | Default configuration, thresholds, weights |

The pipeline reuses the mill graders through `ContractAssertions` rather than reimplementing them, and both use the same `ModelRunners` provider factory.

## Configuration

Anything in `eval.json` can be overridden with a system property: `model`, `judgeModel`, `judge`, `repetitions`, `red`, `gate`, `outputDir`, `baseline`, `live`, `provider`, `saveBaseline`, `forceSaveBaseline`.

Default provider is Ollama. Any OpenAI-compatible HTTP endpoint (LM Studio, vLLM, OpenAI) works too:

```bash
./gradlew evalLive -Dprovider=openai -DopenaiBaseUrl=http://127.0.0.1:1234 -Dmodel=...
```

The provider is recorded in `EvalRun.configuration.provider`. Cost stays `null` unless the provider actually returns a price — token counts are not converted into invented dollars. Cursor / Composer is a different SUT and is not wired up.

Comparing several models on the same dataset, graders, and attempt count:

```bash
./gradlew run --args='--mode=benchmark --models=qwen2.5-coder:7b,other:tag --repetitions=5 --red'
```

## House rules

- Do not compare runs from different dataset versions, or a fixture baseline against a live run.
- Do not raise `liveThresholds.allowedRegression` to make 7b look green; the red rows exist to show it failing.
- Do not tune prompts, graders, or the judge against the holdout split.
- Do not replace a deterministic check with a judge call.
- Do not treat `SKIPPED` as pass or `ERROR` as fail.
- Do not add Ollama to the GitHub-hosted job, or a live job without the Box2 runner labels.
- Do not commit re-recorded fixtures (`-DwriteFixtures=true`) without reading what the model actually wrote.

## Docs

- [START.md](START.md) — the 3-minute mill walkthrough (Russian)
- [docs/evaluation-methodology.md](docs/evaluation-methodology.md) — SSOT for statuses, metrics, comparability, gate
- [docs/dataset-versioning.md](docs/dataset-versioning.md) — when and how to bump a version
- [docs/adding-a-case.md](docs/adding-a-case.md) · [docs/adding-a-grader.md](docs/adding-a-grader.md)
- [src/test/java/eval/pack/README.md](src/test/java/eval/pack/README.md) — pack and retriever as the SUT
