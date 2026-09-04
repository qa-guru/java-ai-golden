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
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Ollama HTTP adapter. Mill live tests and the eval pipeline both go through {@link #complete}.
 * {@code java.net.http.HttpClient} does not send Basic auth from {@code user:pass@host};
 * credentials come from {@code OLLAMA_USER}/{@code OLLAMA_PASSWORD} or URL userinfo, as an
 * {@code Authorization} header.
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
        Target target = resolve(
                System.getProperty("ollamaHost", defaultHost()),
                System.getenv("OLLAMA_USER"),
                System.getenv("OLLAMA_PASSWORD"));
        URI uri = URI.create(target.base() + "/api/chat");

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
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout())
                    .header("Content-Type", "application/json");
            if (target.authorization() != null) {
                builder.header("Authorization", target.authorization());
            }
            request = builder.POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))).build();
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
                    "Ollama timeout at " + target.base() + ": " + e.getMessage(),
                    e);
        } catch (ConnectException e) {
            throw new EvalInfrastructureException(
                    EvalInfrastructureException.MODEL_UNAVAILABLE,
                    "Ollama unavailable at " + target.base() + ": " + e.getMessage(),
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
                    EvalInfrastructureException.httpKind(response.statusCode()),
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

    private static Duration requestTimeout() {
        String raw = System.getProperty("ollamaTimeoutMinutes");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("OLLAMA_TIMEOUT_MINUTES");
        }
        int minutes = 3;
        if (raw != null && !raw.isBlank()) {
            try {
                minutes = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                minutes = 3;
            }
        }
        return Duration.ofMinutes(Math.max(1, minutes));
    }

    private static String defaultHost() {
        String env = System.getenv("OLLAMA_HOST");
        if (env != null && !env.isBlank()) {
            return env.contains("://") ? env : "http://" + env;
        }
        return "http://127.0.0.1:11434";
    }

    static Target resolve(String hostRaw, String envUser, String envPassword) {
        String withScheme = hostRaw.contains("://") ? hostRaw : "http://" + hostRaw;
        URI parsed = URI.create(trimSlash(withScheme));
        String user = envUser;
        String password = envPassword;
        String info = parsed.getUserInfo();
        if (info != null && !info.isBlank()) {
            int colon = info.indexOf(':');
            if (colon >= 0) {
                user = info.substring(0, colon);
                password = info.substring(colon + 1);
            } else {
                user = info;
            }
        }
        String authorization = null;
        if (user != null && !user.isBlank() && password != null) {
            authorization = "Basic " + Base64.getEncoder()
                    .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        }
        return new Target(stripUserInfo(parsed).toString(), authorization);
    }

    private static URI stripUserInfo(URI uri) {
        try {
            return new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String trimSlash(String host) {
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }

    record Target(String base, String authorization) {
    }
}
