# Dataset versioning

Eval results **must** carry `datasetVersion` and `packDatasetVersion`. You cannot conclude model B is better than A if A ran on `generation-v1` and B on `generation-v3`.

## Current

| Dataset | Version file | Id |
|---|---|---|
| Generation goldens | `src/test/java/eval/generation/dataset.json` | `generation-v1` |
| Pack diet | `src/test/resources/pack/dataset.json` | `pack-v1` |

Pack-only wording that does not change retrieve sets or contracts: bump **`pack-v1` only**. Anything that changes `expect.rag`, `must_not`, refuse behaviour, or case ids: bump **`generation-v1`** (and usually pack if the diet moved).

`RunComparator` is `COMPARISON INVALID` when generation versions differ, or when **both** runs have a pack version and they differ. A legacy baseline with `packDatasetVersion: null` still compares.

## When to bump generation

Increment `generation-v1` → `generation-v2` if you:

- add, remove, or rename a case id
- change a prompt in a way that changes retriever diet or expected Java
- change `expect` or `must_not`
- change fixtures that are the deterministic oracle
- change retriever scoring so `expect.rag` would be wrong without a matching JSONL edit

Do **not** bump only because a live model started passing a red row. Record that in `baselines/live-generation-v1.json`, not by editing goldens to flatter the model.

Do **not** delete a failing live case to raise percentages. The mill’s red rows exist to show 7b failing.

## When to bump pack

Increment `pack-v1` → `pack-v2` if you change RAG chunk ids, `related`/`index` enough to change retrieve sets, skill/rules refuse surface, or ADR 009 in a way mill tests would notice.

## How to bump

1. Edit the relevant `dataset.json` `version` and `changelog`.
2. Keep JSONL `id` stable unless you are intentionally replacing a case.
3. Re-record fixtures only with `-DwriteFixtures=true` after you have **read** the output. Do not commit 7b echo.
4. Re-run `./gradlew test evalDeterministic`.
5. Write a **new** fixture baseline (`baselines/generation-v2.json`) via `-DsaveBaseline=…`. Do not compare v2 runs to `generation-v1.json`.
6. Re-capture live baseline separately if pack/generation oracles changed.
7. Update START.md / README tables if ids or mill steps changed.
