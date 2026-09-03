# Example eval report (deterministic, generation-v1)

This is a **fixture** eval: every recorded golden still matches the contract and retriever oracle. It is not a live-model score. `Commit` and latency vary per machine; labels match `ConsoleReporter`.

```
AI EVAL
=======
Model:        qwen2.5-coder:7b
Judge:        off
Dataset:      generation-v1 / pack-v1
Commit:       e2be885
Repetitions:  1
EXECUTION
---------
Cases:        8
Executed:     8
Passed:       8
Failed:       0
Skipped:      0
Errors:       0
Attempts:     8 pass / 0 fail / 0 error / 0 skip
Pass rate:    100.0% (8 / 8) of executed
Coverage:     100.0%
METRICS
-------
Overall:                        100.0% (8 / 8)
Contract:                       100.0%
Judge:                          n/a
Retrieval:                      100.0%
Negative:                       100.0%
Refusal:                        100.0%
Hallucination (fail rate):      0.0%
Layer:                          100.0%
RAG:                            100.0%
Unstable:                       n/a
Slice generation:               100.0%
Slice rag:                      100.0%
Slice retrieval:                100.0%
Slice layer:                    100.0%
Slice negative:                 100.0%
Slice hallucination (pass rate):100.0%
Slice refusal:                  100.0%
Weighted:     1.000 (secondary)
Latency avg:  3 ms
Latency p95:  6 ms
QUALITY GATE
------------
PASS
Allowed because every recorded rule passed.
```

Markdown from the same pipeline: `build/eval/<runId>/eval-report.md` after `./gradlew evalDeterministic`. Semantics: [evaluation-methodology.md](../evaluation-methodology.md).
