package eval.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eval.domain.TokenUsage;
import eval.provider.EvalInfrastructureException;
import eval.provider.ModelResponse;
import eval.provider.ModelRunner;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Ollama HTTP adapter. Mill live tests and the eval pipeline both go through {@link #complete}.
 */
public final class OllamaClient implements ModelRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public OllamaClient() {
    }

    @Override
    public ModelResponse complete(String system, String user, String model)
            throws EvalInfrastructureException, InterruptedException {
        String host = System.getProperty("ollamaHost", defaultHost());
        URI uri = URI.create(trimSlash(host) + "/api/chat");

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        ObjectNode options = body.putObject("options");
        options.put("temperature", 0);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system == null ? "" : system);
        messages.addObject().put("role", "user").put("content", user == null ? "" : user);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
        } catch (IOException e) {
            throw new EvalInfrastructureException(EvalInfrastructureException.PROVIDER_ERROR, e.getMessage(), e);
        }

        long started = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new EvalInfrastructureException(
                    EvalInfrastructureException.TIMEOUT,
                    "Ollama timeout at " + host + ": " + e.getMessage(),
                    e);
        } catch (ConnectException e) {
            throw new EvalInfrastructureException(
                    EvalInfrastructureException.MODEL_UNAVAILABLE,
                    "Ollama unavailable at " + host + ": " + e.getMessage(),
                    e);
        } catch (IOException e) {
            throw new EvalInfrastructureException(
                    EvalInfrastructureException.MODEL_UNAVAILABLE,
                    "Ollama I/O: " + e.getMessage(),
                    e);
        }
        long durationMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new EvalInfrastructureException(
                    EvalInfrastructureException.HTTP_ERROR,
                    "Ollama HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new EvalInfrastructureException(EvalInfrastructureException.PARSER_ERROR, "Ollama JSON: " + e.getMessage(), e);
        }
        if (root.hasNonNull("error")) {
            throw new EvalInfrastructureException(
                    EvalInfrastructureException.PROVIDER_ERROR,
                    "Ollama error: " + root.get("error").asText());
        }
        String content = root.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new EvalInfrastructureException(EvalInfrastructureException.EMPTY_RESPONSE, "Ollama empty message");
        }
        Integer input = intOrNull(root, "prompt_eval_count");
        Integer output = intOrNull(root, "eval_count");
        return new ModelResponse(content, TokenUsage.of(input, output), durationMs);
    }

    private static Integer intOrNull(JsonNode root, String field) {
        if (!root.has(field) || !root.get(field).isNumber()) {
            return null;
        }
        return root.get(field).asInt();
    }

    private static String defaultHost() {
        String env = System.getenv("OLLAMA_HOST");
        if (env != null && !env.isBlank()) {
            return env.contains("://") ? env : "http://" + env;
        }
        return "http://127.0.0.1:11434";
    }

    private static String trimSlash(String host) {
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }
}
