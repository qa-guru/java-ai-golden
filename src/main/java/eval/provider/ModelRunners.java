package eval.provider;

import eval.execution.EvalConfig;
import eval.generation.OllamaClient;

public final class ModelRunners {

    public static final String OLLAMA = "ollama";
    public static final String OPENAI = "openai";

    private ModelRunners() {
    }

    public static ModelRunner create(EvalConfig config) {
        String provider = config.provider();
        return switch (provider) {
            case OPENAI -> new OpenAiCompatibleClient(config.openaiBaseUrl(), config.openaiApiKey());
            case OLLAMA -> new OllamaClient();
            default -> throw new IllegalArgumentException(
                    "Unknown provider '" + provider + "' (use ollama|openai)");
        };
    }
}
