# Example eval report (deterministic, generation-v1)

This is a **fixture** eval: every recorded golden still matches the contract and retriever oracle. It is not a live-model score.

```
AI EVAL
=======
Model:        qwen2.5-coder:7b
Judge:        off
Dataset:      generation-v1
Repetitions:  1
CASES
-----
Total:        8
Attempts:     8
Passed:       8
Failed:       0
Errors:       0
METRICS
-------
Overall:        100.0%
Contract:       100.0%
Judge:          n/a
Retrieval:      100.0% (5 / 5)
Negative:       100.0% (5 / 5)
Refusal:        100.0% (3 / 3)
Hallucination:  0.0% (0 / 2)
Layer:          100.0%
RAG:            100.0%
QUALITY GATE
------------
PASS
```

Markdown from the same pipeline: `build/eval/<runId>/eval-report.md` after `./gradlew evalDeterministic`.
