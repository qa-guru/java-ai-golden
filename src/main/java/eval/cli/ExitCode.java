package eval.cli;

public final class ExitCode {
    public static final int SUCCESS = 0;
    public static final int USAGE = 1;
    public static final int QUALITY_GATE_FAILED = 2;
    public static final int INFRASTRUCTURE_FAILURE = 3;
    public static final int COMPARISON_INVALID = 4;

    private ExitCode() {
    }
}
