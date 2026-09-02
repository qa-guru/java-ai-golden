# Dataset versioning

Eval results **must** carry `datasetVersion`. You cannot conclude model B is better than A if A ran on `generation-v1` and B on `generation-v3`.

## Current

| Dataset | Version file | Id |
|---|---|---|
| Generation goldens | `src/test/java/eval/generation/dataset.json` | `generation-v1` |
| Pack diet | `src/test/resources/pack/` | not separately numbered; oracle is `expect.rag` in the generation JSONL |

Pack-only wording changes that do not change retrieve sets or contracts do not require a bump. Anything that changes `expect.rag`, `must_not`, refuse behaviour, or case ids **does**.

## When to bump

Increment `generation-v1` → `generation-v2` (and add a changelog line) if you:

- add, remove, or rename a case id
- change a prompt in a way that changes retriever diet or expected Java
- change `expect` or `must_not`
- change fixtures that are the deterministic oracle
- change retriever scoring so `expect.rag` would be wrong without a matching JSONL edit

Do **not** bump only because a live model started passing a red row. Record that in a live baseline, not by editing goldens to flatter the model.

Do **not** delete a failing live case to raise percentages. The mill’s red rows exist to show 7b failing.

## How to bump

1. Edit `dataset.json` `version` and `changelog`.
2. Keep JSONL `id` stable unless you are intentionally replacing a case (then treat it as a new id; do not reuse).
3. Re-record fixtures only with `-DwriteFixtures=true` after you have **read** the output. Do not commit 7b echo (`Invalid password`, mixed-layer Java, …).
4. Re-run `./gradlew test evalDeterministic`.
5. Write a **new** baseline (`baselines/generation-v2.json`) via `--save-baseline=…`. Do not compare v2 runs to `generation-v1.json`.
6. Update START.md / README tables if ids or mill steps changed.

## Compatibility

`RunComparator` refuses to score two runs when `datasetVersion` differs. That is intentional.
