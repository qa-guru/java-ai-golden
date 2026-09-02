# Adding a golden case

1. **Assign a stable id** that will not change if the JSONL is sorted. Prefer a story name (`login-wrong-password-e2e`), not a line number. New independent datasets may use `GEN-001` / `PACK-001`.
2. **Define input** — `prompt` the agent actually sees.
3. **Define expected behaviour** — `expect.layer`, `class`, `contains`, `status`, `refuse`, `rag` (retriever oracle, not prompt stuffing).
4. **Hard constraints** — `must_not` strings (hallucinated copy, wrong layer APIs, `git commit`, …).
5. **Soft criteria** — judge MODE in `rubric-judge.md` if live judge should score style/completeness. Soft must not duplicate a hard check.
6. **Negative / red** — `expect.refuse=true` for policy refusals; `expect.red=true` if live 7b is currently expected to fail (still keep a **passing fixture**).
7. **Fixture** — `fixtures/<id>.out.md` that passes `ContractGrader`.
8. **Grader test** — add a `ContractAssertionsTest` (or `ContractGraderEdgeTest`) row that fails on the bug you care about (polite refuse without `Отказ.`, `@Step` on `*Tests`, …).
9. **Retriever** — if not refuse, set `expect.rag` to the actual `LexicalRetriever.retrieve(prompt)` set (2–4 ids). `RetrieverTest` will enforce it.
10. **Bump dataset version** — [dataset-versioning.md](dataset-versioning.md).
11. Do **not** add a case to holdout in order to tune the grader. Holdout is final evaluation only.

Do not add a case whose only purpose is to make a dashboard look greener.
