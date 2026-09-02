package eval.domain;

public enum CaseRegression {
    NEW_FAILURE,
    RECOVERED,
    UNCHANGED_PASS,
    UNCHANGED_FAIL,
    UNCHANGED_SKIPPED,
    ADDED,
    REMOVED,
    /** @deprecated use {@link #UNCHANGED_FAIL} */
    @Deprecated
    STILL_FAILING,
    /** @deprecated use {@link #UNCHANGED_PASS} */
    @Deprecated
    STILL_PASSING
}
