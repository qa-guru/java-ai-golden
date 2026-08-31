package eval.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class OllamaClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private OllamaClient() {
    }

    static String chat(String system, String user) throws IOException, InterruptedException {
        String model = System.getProperty("model", "qwen2.5-coder:7b");
        String host = System.getProperty("ollamaHost", defaultHost());
        URI uri = URI.create(trimSlash(host) + "/api/chat");

        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        ObjectNode options = body.putObject("options");
        options.put("temperature", 0);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Ollama HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode root = MAPPER.readTree(response.body());
        if (root.hasNonNull("error")) {
            throw new IOException("Ollama error: " + root.get("error").asText());
        }
        String content = root.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IOException("Ollama empty message");
        }
        return content;
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
