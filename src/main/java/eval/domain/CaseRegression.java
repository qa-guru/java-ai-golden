package eval.domain;

public enum CaseRegression {
    NEW_FAILURE,
    RECOVERED,
    UNCHANGED_PASS,
    UNCHANGED_FAIL,
    UNCHANGED_SKIPPED,
    /** Quality (PASS/FAIL) or SKIPPED → ERROR. Not a model-quality regression. */
    NEW_ERROR,
    UNCHANGED_ERROR,
    /** ERROR → PASS/FAIL/SKIPPED. Not a quality recovery. */
    INFRA_RESOLVED,
    ADDED,
    REMOVED,
    /** @deprecated use {@link #UNCHANGED_FAIL} */
    @Deprecated
    STILL_FAILING,
    /** @deprecated use {@link #UNCHANGED_PASS} */
    @Deprecated
    STILL_PASSING
}
