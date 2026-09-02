# Evaluation methodology

SSOT for what numbers in this repo mean. Implementation lives under `eval.*`; mill JUnit tests are the teaching surface.

## Case

A **case** is one golden row: stable `id`, `prompt`, `expect`, `must_not`.

Ids are unique in the JSONL and do not depend on file order. Current set (`generation-v1`):

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

A case may belong to several **kinds** (generation, retrieval, negative, hallucination, refusal, layer, RAG). Kinds are derived from the row; they are not a second id.

## Attempt

One execution of a case:

- Deterministic: grade the recorded fixture. Always 1 attempt.
- Live: one `ModelRunner.complete` call. `--repetitions=N` → N attempts.

Judge is a second model call on the same attempt, not a separate case.

## Pass

Attempt `PASS` = deterministic **contract** passed (hard). Retrieval mismatch on a generate row also fails the case.

Judge `REJECT` does **not** flip a contract `PASS` into overall `FAIL` (mill: live JUnit stays green; `judgeAcceptRate` drops). Judge `ACCEPT` does **not** flip a contract `FAIL` into `PASS`.

Case `PASS` = every quality attempt `PASS` (successRate 100% of quality attempts).

## Failure

Attempt `FAIL` = contract (or retrieval) hard fail. This is **model / pack / fixture quality**, not infra.

## Infrastructure error

Attempt `ERROR` = provider/runtime: connection refused, HTTP 4xx/5xx, empty message, interrupt.

`ERROR` is excluded from pass rates. If there are no quality attempts left, the CLI exits `3 INFRASTRUCTURE_FAILURE` instead of advertising 0% quality.

## Skip

Live **red** rows (`expect.red`) without `--red` / `-Dred=true` are `SKIPPED`. Fixtures for those rows still run in deterministic mode — they are valid goldens.

## Metrics

Let \(P\) be passes and \(T\) quality attempts (PASS+FAIL).

\[
\text{overallPassRate} = P / T
\]

Never \(\frac{1}{n}\sum_i \text{caseRate}_i\) when attempt counts differ.

Retrieval uses **cases** with applicable retriever oracle (non-refuse rows), not live tokens.

Hallucination rate is **fails / attempts** on `hallucinate-*` (lower is better).

Undefined (`T=0`) prints `n/a` and is skipped in the weighted formula.

## Repetitions

Live only. Same prompt, same graders, N independent generations (Ollama temperature is 0 but sampling and model updates still drift).

Report both per-case `k/N` and global \(P/T\).

## Comparing models

Fair comparison requires:

1. Same `datasetVersion`
2. Same case ids (union reported; extras are ADDED/REMOVED)
3. Same graders / thresholds
4. Same `repetitions` and `includeRed` (live 1-shot vs nightly 5-rep+red → `COMPARISON INVALID`)

Mismatch of dataset version, pack version (when both set), or live protocol → `COMPARISON INVALID`.

## Regression

Binary per case from 100% vs not-100% quality attempts:

- `NEW_FAILURE` — baseline pass, candidate fail
- `RECOVERED` — baseline fail, candidate pass
- `STILL_FAILING` / `STILL_PASSING`

Rate drops on a case that was already failing stay `STILL_FAILING` but still show in the fraction column.

## Quality gate

Two rule families, both optional per metric:

1. **Absolute** — pass rate ≥ min; hallucination ≤ max
2. **Delta** — candidate ≥ baseline − `allowedRegression` (hallucination: ≤ baseline + δ)

Gate fail → exit 2. Does not rewrite metrics.

Deterministic PR gate uses 100% / 0% hallucination on **fixtures**. That rejects a broken golden or grader, not a weak 7b.

Live 1-shot and nightly use the same `liveThresholds` delta, but **different baseline files**. Mixing protocols is `COMPARISON INVALID`, not a percentage.

## Why contract outranks the judge

The grader is tested, cheap, and stable. The judge is another LLM: nondeterministic, schema-fragile, and easy to sweet-talk. Soft scores (clarity, usefulness) must not wash out a forbidden citation, mixed layer, or hallucinated error string.

## What this mill does not claim

- Fixture `overallPassRate = 100%` ≠ live model quality.
- Ollama token counts ≠ billed USD (`estimatedCost` stays null).
- Lexical retriever match ≠ IR precision@k.
- Judge `PENDING` ≠ accept.
