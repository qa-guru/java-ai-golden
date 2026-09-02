package eval.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eval.domain.TokenUsage;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * OpenAI-compatible {@code POST /v1/chat/completions}.
 * Cost stays {@code null}: this adapter does not invent USD from token counts.
 */
public final class OpenAiCompatibleClient implements ModelRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String baseUrl;
    private final String apiKey;

    public OpenAiCompatibleClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com" : trimSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    @Override
    public ModelResponse complete(String system, String user, String model)
            throws EvalInfrastructureException, InterruptedException {
        URI uri = URI.create(baseUrl + "/v1/chat/completions");
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system == null ? "" : system);
        messages.addObject().put("role", "user").put("content", user == null ? "" : user);

        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        } catch (IOException e) {
            throw new EvalInfrastructureException(EvalInfrastructureException.PROVIDER_ERROR, e.getMessage(), e);
        }
        if (!apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        long started = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new EvalInfrastructureException(
                    EvalInfrastructureException.TIMEOUT,
                    "OpenAI-compatible timeout at " + baseUrl + ": " + e.getMessage(),
                    e);
        } catch (ConnectException e) {
            throw new EvalInfrastructureException(
                    EvalInfrastructureException.MODEL_UNAVAILABLE,
                    "OpenAI-compatible endpoint unavailable at " + baseUrl + ": " + e.getMessage(),
                    e);
        } catch (IOException e) {
            throw new EvalInfrastructureException(
                    EvalInfrastructureException.MODEL_UNAVAILABLE,
                    "OpenAI-compatible I/O: " + e.getMessage(),
                    e);
        }
        long durationMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new EvalInfrastructureException(
                    EvalInfrastructureException.httpKind(response.statusCode()),
                    "HTTP " + response.statusCode() + ": " + response.body());
        }
        return parse(response.body(), durationMs);
    }

    static ModelResponse parse(String body, long durationMs) throws EvalInfrastructureException {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (IOException e) {
            throw new EvalInfrastructureException(
                    EvalInfrastructureException.PARSER_ERROR, "JSON: " + e.getMessage(), e);
        }
        if (root.hasNonNull("error")) {
            JsonNode err = root.get("error");
            String msg = err.isTextual() ? err.asText() : err.path("message").asText(err.toString());
            throw new EvalInfrastructureException(EvalInfrastructureException.PROVIDER_ERROR, "provider error: " + msg);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new EvalInfrastructureException(EvalInfrastructureException.EMPTY_RESPONSE, "no choices");
        }
        String content = choices.get(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new EvalInfrastructureException(EvalInfrastructureException.EMPTY_RESPONSE, "empty message");
        }
        JsonNode usage = root.path("usage");
        Integer input = intOrNull(usage, "prompt_tokens");
        Integer output = intOrNull(usage, "completion_tokens");
        return new ModelResponse(content, TokenUsage.of(input, output), durationMs);
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isNumber()) {
            return null;
        }
        return node.get(field).asInt();
    }

    private static String trimSlash(String host) {
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }
}
