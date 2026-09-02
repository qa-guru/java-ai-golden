package eval.domain;

/**
 * Attempt / case outcome. Infrastructure must not be recorded as FAIL.
 */
public enum EvalStatus {
    PASS,
    FAIL,
    SKIPPED,
    ERROR
}
