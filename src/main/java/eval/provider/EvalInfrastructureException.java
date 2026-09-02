package eval.provider;

import java.io.IOException;

/**
 * Provider / runtime failure. Not a quality FAIL.
 */
public final class EvalInfrastructureException extends IOException {

    public static final String MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";
    public static final String HTTP_ERROR = "HTTP_ERROR";
    public static final String EMPTY_RESPONSE = "EMPTY_RESPONSE";
    public static final String PROVIDER_ERROR = "PROVIDER_ERROR";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String PARSER_ERROR = "PARSER_ERROR";
    public static final String JUDGE_ERROR = "JUDGE_ERROR";

    private final String kind;

    public EvalInfrastructureException(String kind, String message) {
        super(message);
        this.kind = kind == null ? PROVIDER_ERROR : kind;
    }

    public EvalInfrastructureException(String kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind == null ? PROVIDER_ERROR : kind;
    }

    public String kind() {
        return kind;
    }
}
