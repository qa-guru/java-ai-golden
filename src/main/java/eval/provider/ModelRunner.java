package eval.provider;

/**
 * Model adapter. Core eval talks to this, not to a vendor SDK.
 */
public interface ModelRunner {
    ModelResponse complete(String system, String user, String model) throws EvalInfrastructureException, InterruptedException;
}
