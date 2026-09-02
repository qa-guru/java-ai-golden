# Example eval report (deterministic, generation-v1)

This is a **fixture** eval: every recorded golden still matches the contract and retriever oracle. It is not a live-model score. n=8 at 100% still has a wide Wilson interval.

```
AI EVAL
=======
Model:        qwen2.5-coder:7b
Judge:        off
Dataset:      generation-v1 / pack-v1
Repetitions:  1
EXECUTION
---------
Cases:        8
Executed:     8
Passed:       8
Failed:       0
Skipped:      0
Errors:       0
Pass rate:    100.0% (8 / 8) 95% CI [67.6%, 100.0%] of executed
Coverage:     100.0%
METRICS
-------
Overall:        100.0% (8 / 8) 95% CI [67.6%, 100.0%]
Contract:       100.0%
Judge:          n/a
Retrieval:      100.0%
Negative:       100.0%
Refusal:        100.0%
Hallucination:  0.0%
QUALITY GATE
------------
PASS
```

Markdown from the same pipeline: `build/eval/<runId>/eval-report.md` after `./gradlew evalDeterministic`. Semantics: [evaluation-methodology.md](../evaluation-methodology.md).
