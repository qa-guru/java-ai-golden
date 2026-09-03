# Evaluation methodology

SSOT for what numbers in this repo mean. README is the command surface. Do not copy conflicting rules into other docs.

## Lifecycle

```
Dataset
  → Evaluation target (model + prompt + pack + configuration + experiment)
  → Execution
       ├── deterministic grading (hard)
       ├── retrieval grading (hard)
       └── LLM judge (soft, optional)
  → Case results (PASS / FAIL / ERROR / SKIPPED)
  → Metrics (attempt-weighted) + stability
  → Repeated runs
  → Baseline / candidate
  → Paired comparison
  → Regression detection (overall + per-case)
  → Quality gate → CLI / CI / JSON+Markdown reports
```

## CASE

One unique golden scenario. Stable `id` in JSONL (not the line index). File order must not change identity.

Current **development** set (`generation-v1`, 8 cases):

| id | Role |
|---|---|
| `login-401-api` | API 401 + canon message |
| `login-wrong-password-e2e` | Form-negative (lab 36) |
| `login-valid-e2e` | Form-happy |
| `hallucinate-locator` | Red: locator in test |
| `hallucinate-error` | Red: echo `Invalid password` |
| `mixed-layer` | Refuse mixed UI+API |
| `read-all-rag` | Refuse “read all RAG” |
| `jailbreak-env` | Refuse commit `.env` |

A case may have several **kinds** derived from the row (`generation`, `retrieval`, `negative`, `hallucination`, `refusal`, `layer`, `rag`). Slice metrics use those kinds; the aggregator does not hardcode a product category list.

**Holdout** (`holdout-v1`, 8 cases, not in development): `src/main/resources/eval/generation/holdout/`. **Do not use holdout to tune prompt, grader, or judge.** Default pipeline loads development only. Final check: `./gradlew evalHoldout`.

| id | Role |
|---|---|
| `holdout-empty-password-e2e` | Form-negative, empty password |
| `holdout-typo-password-e2e` | Form-negative, typo password |
| `holdout-no-dollar-e2e` | Form-negative, locators not in the test |
| `holdout-happy-welcome-e2e` | Form-happy |
| `holdout-api-bad-creds` | API 401 |
| `holdout-refuse-api-key` | Refuse commit API keys |
| `holdout-refuse-dump-rag` | Refuse dump-all RAG |
| `holdout-refuse-mix-layers` | Refuse mixed form + 401 |

## ATTEMPT

One actual execution of a case.

- Deterministic: grade the recorded fixture. Always 1 attempt. No LLM.
- Live: one `ModelRunner.complete` call. `--repetitions=N` → N attempts.

Judge is extra model call(s) on the same attempt, not a separate case. `--judge-repetitions=N` (N>1) is an opt-in judge-consistency mode.

## RUN

One `EvalRun`: a set of evaluation attempts over a dataset split, with one evaluation target.

Target = **model + prompt/pack + configuration + experimentId**, not model alone. `--experiment=prompt-v13` records that.

## PASS

Attempt `PASS` = every **hard** requirement held (contract + applicable retrieval).

Judge `REJECT` does **not** flip a contract `PASS` into overall `FAIL`. Judge `ACCEPT` does **not** flip a contract `FAIL` into `PASS`.

Case `PASS` = every quality attempt `PASS` (successRate 100% of quality attempts).

## FAIL

Attempt `FAIL` = hard criteria violated. This is **model / pack / fixture quality**, not infrastructure.

Also called a quality failure. Taxonomy: `category`, `severity`, `grader`, `reason`.

## ERROR

Evaluation infrastructure could not complete the case correctly.

Kinds include: `MODEL_UNAVAILABLE`, `HTTP_ERROR`, `EMPTY_RESPONSE`, `PROVIDER_ERROR`, `TIMEOUT`, `PARSER_ERROR`, `JUDGE_ERROR`, `RATE_LIMIT` (HTTP 429), `RETRIEVER_MISS` (live prompt build / empty retrieve on a non-refuse row; the case is recorded and the run continues).

Judge **HTTP** timeout/unavailable is attempt `ERROR` (`JUDGE_ERROR`). Malformed judge JSON, missing fields, out-of-range score, or VERDICT/JSON contradiction is **not** a quality FAIL: the attempt stays on the hard contract, and the judge result is `PENDING` with `schemaValid=false` (excluded from `judgeAcceptRate`).

`ERROR != FAIL`. Errors are excluded from pass rates. A run with only errors exits `3 INFRASTRUCTURE_FAILURE` instead of advertising 0% model quality.

`QUALITY_FAILURE` is `FAIL`, not `ERROR`.

## SKIPPED

The case was intentionally not executed (live red rows without `--red`).

`SKIPPED != PASS`. Skipped cases are not in the pass-rate denominator.

```
Cases:       40
Executed:    30
Passed:      30
Failed:       0
Skipped:     10
Pass rate: 100% of executed
Coverage:   75%
```

A run with **zero quality attempts** (empty dataset or all skipped) is **not** a green gate.

## REGRESSION / IMPROVEMENT

Paired on the same case ids:

| Baseline → candidate | Name |
|---|---|
| PASS → FAIL | `NEW_FAILURE` (regression) |
| FAIL → PASS | `RECOVERED` (improvement) |
| PASS → PASS | `UNCHANGED_PASS` |
| FAIL → FAIL | `UNCHANGED_FAIL` |
| SKIPPED → SKIPPED | `UNCHANGED_SKIPPED` (not a fail) |
| PASS/FAIL/SKIPPED → ERROR | `NEW_ERROR` (infrastructure, **not** a quality regression) |
| ERROR → ERROR | `UNCHANGED_ERROR` |
| ERROR → PASS/FAIL/SKIPPED | `INFRA_RESOLVED` (not a quality recovery) |

McNemar uses only `NEW_FAILURE` / `RECOVERED` pairs. An Ollama timeout is `NEW_ERROR`; it must not look like a model regression. The process still exits `3 INFRASTRUCTURE_FAILURE` if any attempt is `ERROR`.

Overall improvement must not hide a `NEW_FAILURE`. A **CRITICAL** new failure fails the gate even if overall delta is within budget.

Live delta (`--gate`):

1. If the rate is within `allowedRegression` of the baseline → `NO_REGRESSION`.
2. If the rate is worse than allowed → `REGRESSION` (gate FAIL).
3. A **CRITICAL** `NEW_FAILURE` still fails the gate even when the overall drop is within budget.
4. Absolute thresholds (fixture 100%) are unchanged: one fixture fail is still a gate FAIL.

`comparison.decision` is `REGRESSION` | `NO_REGRESSION` | `COMPARISON_INVALID`. McNemar stays informational.

## Metrics

Let \(P\) be passes and \(T\) quality attempts (`PASS+FAIL` only).

\[
\text{overallPassRate} = P / T
\]

Never the unweighted mean of per-case percentages when attempt counts differ.

Example: 3 cases × 5 repetitions = 15 attempts. 12 PASS + 3 FAIL → 80%, not the mean of case rates.

Retrieval uses **cases** with a retriever oracle (non-refuse rows).

Hallucination rate is **fails / attempts** on `hallucinate-*` (lower is better).

Undefined (`T=0`) prints `n/a`. Empty dataset is **not** 100%.

### Stability

Quality ≠ stability.

- 5/5 PASS → high quality, high stability
- PASS FAIL PASS FAIL PASS → medium quality, low stability (`unstableCaseRate`)

A case is unstable when `qualityAttempts >= 2` and not all outcomes match.

### Slices

`metrics.slices` is pass/attempts grouped by each case's kinds. Example keys: `generation`, `retrieval`, `rag`, `negative`, `refusal`.

## Dataset identity

- `datasetVersion` (manifest)
- `datasetHash` — SHA-256 of canonical JSON of cases **sorted by id**
- `packHash` — SHA-256 of pack files **sorted by relative path**. The four committed `baselines/*.json` snapshots include it. `RunComparator` still treats a **legacy** file that omitted `packHash` as comparable (invalid only when both sides recorded a hash and they differ).

Same bytes after reorder → same hash. Edit a prompt → different `datasetHash`. Edit a RAG chunk without bumping `pack-v1` → different `packHash` (and `COMPARISON INVALID` when **both** runs have a hash). Duplicate or missing `id` → hard error.

Do not compare runs with different `datasetVersion` or different `datasetHash`. Missing hash on one side only is also `COMPARISON INVALID` for **dataset** hash (legacy snapshots must be recaptured). Missing `packHash` on one side only is **not** invalid (comparator behavior for old files; committed snapshots all have `packHash`).

## Configuration fingerprint

`configFingerprint` hashes: execution mode, model, judge (or `off`), provider, repetitions, includeRed, datasetVersion, datasetHash, packVersion, packHash, experimentId, gitCommit.

It does **not** hash runId, timestamp, duration, outputDir, artifact mode, or Java patch level. `RunConfiguration.javaVersion` is `java.specification.version` (e.g. `21`) for “what produced this”, not a comparison key.

## Fair comparison

Required to be the same:

1. `datasetVersion`
2. `datasetHash` (if both recorded)
3. pack version (if both recorded)
4. `packHash` (if both recorded)
5. execution mode (fixture vs live)
6. `repetitions` and `includeRed`
7. judge enabled / judge model (when either side judges)

Mismatch → `COMPARISON INVALID`.

Different **models, prompts, experiments** are allowed — that is the experiment.

## Quality gate

Two rule families, both optional per metric:

1. **Absolute** — pass rate ≥ min; hallucination ≤ max
2. **Delta** — rate vs `allowedRegression`. Worse than allowed → FAIL.

Absolute pass does **not** waive a delta fail: baseline 20/20, candidate 5/20, absolute min 20%, max regression 2pp → absolute PASS, delta **REGRESSION**.

CRITICAL new failures fail the gate.

Gate fail → exit 2. Does not rewrite metrics.

Deterministic PR gate: 100% / 0% hallucination on **fixtures**.

Live: `liveThresholds` delta vs a **live** baseline file. Missing, fixture, or protocol-mismatched live baseline with `--gate` → gate **FAIL** (not a skipped-as-PASS, not a fake 100%). Capture without `--gate` leaves `qualityGate` unset. Fixture baseline must not be used as a live score.

## Hard vs soft

**HARD** (deterministic grader): forbidden behavior, hallucination strings, wrong layer, invalid contract, safety (`git commit` / Java on refuse), explicit `must_not` / `contains` / RAG header vs retriever.

**SOFT** (LLM judge): clarity, readability, completeness, style, maintainability.

Contract outranks the judge. Soft scores cannot wash out a forbidden citation.

## Judge calibration

`src/main/resources/eval/generation/calibration/judge-calibration.jsonl` is a **separate validation layer**. It does not affect production eval or the quality gate.

It reports accuracy, precision, recall, F1, confusion matrix (ACCEPT = positive class). n≈20 is too small for a published judge-accuracy claim; the caveat is part of the report.

- Canned: `./gradlew evalJudgeCalibration` (uses stored `judgeOutput`).
- Live: `./gradlew evalJudgeCalibrationLive` (re-judges `candidate` on REJECT rows, fixtures on ACCEPT). Not in GitHub Actions. Not `--mode=live`.

## Judge consistency

`--judge-repetitions=3` on live. ACCEPT/REJECT/ACCEPT → judge stability 66.7% (majority / N). Default N=1 (off).

## Error taxonomy

Failures in the Markdown report:

```
GEN-017  FAIL  category: HALLUCINATION  severity: CRITICAL  grader: ContractGrader  reason: ...
```

Severity: `CRITICAL` (hallucinated citation, Java on refuse) · `HIGH` (layer/contract/forbidden) · `MEDIUM` (`@Step` on `*Tests`) · `LOW` unused in mill hard checks.

## Cost and latency

Tokens: `inputTokens` / `outputTokens` / `totalTokens` when the provider returns them.

`estimatedCost` is **null** unless a provider actually returns a price. We do not invent USD.

Latency: min / avg / median / p95 / max around the model call (live) or fixture grading (deterministic).

## History

Each artifact write appends one line to `build/eval/history.jsonl`: timestamp, commit, experiment, model, dataset, overall, quality gate. Not a database.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | SUCCESS (quality gate passed, or not applied, and infra OK) |
| 1 | USAGE |
| 2 | `QUALITY_GATE_FAILED` (including empty / all-skipped) |
| 3 | `INFRASTRUCTURE_FAILURE` |
| 4 | `COMPARISON_INVALID` |

## CI

| Where | Command | LLM |
|---|---|---|
| PR (GitHub Actions, `ubuntu-latest`) | `./gradlew test evalDeterministic evalRegression evalHoldout evalHoldoutRegression evalJudgeCalibration` | no |
| Live smoke (Box2 self-hosted, dispatch) | `evalLive` then compare-only vs live baseline | yes once, skip red |
| NIGHTLY (Box2 self-hosted, cron) | `evalNightly` then compare-only vs nightly baseline | yes once, red + 5 reps |
| Local live | same Gradle tasks, local Ollama | yes |

GitHub-hosted `ubuntu-latest` has no Ollama. Do not make live LLM required for every PR.

## What this mill does not claim

- Fixture `overallPassRate = 100%` ≠ live model quality.
- 8/8 on fixtures is not a live model score.
- Ollama token counts ≠ billed USD.
- Lexical retriever match ≠ IR precision@k.
- Judge `PENDING` ≠ accept.
- Calibration corpus accuracy ≠ production judge accuracy.
- Holdout of 8 cases is a split discipline, not a large generalization test.
