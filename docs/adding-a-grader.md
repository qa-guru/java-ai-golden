# Adding a grader

1. Prefer extending `eval.grading.ContractGrader` or adding a sibling grader that returns a result record.
2. Do not throw AssertionError from production grading — collect violations.
3. Unit tests: happy fixture, empty, null, malformed, partial, multiple violations, unicode, huge input.
4. Wire the result into `CaseResult` / `MetricsAggregator` only if you introduce a **new** metric; otherwise keep it as extra violations on the contract.
5. Mill JUnit: keep `ContractAssertions.assertMatches` as a thin assert on the grader so START.md demos still fail loudly.
6. Never implement a hard rule as “ask the judge”.
