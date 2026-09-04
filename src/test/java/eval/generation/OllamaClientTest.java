package eval.generation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("eval")
@Tag("generation")
@DisplayName("Ollama endpoint and Basic auth")
class OllamaClientTest {

    @Test
    void loopbackNeedsNoAuthorization() {
        OllamaClient.Target target = OllamaClient.resolve("http://127.0.0.1:11434", null, null);
        assertEquals("http://127.0.0.1:11434", target.base());
        assertNull(target.authorization());
    }

    @Test
    void envCredentialsBecomeAuthorizationHeader() {
        OllamaClient.Target target = OllamaClient.resolve("https://ollama.qa.guru", "qaguru", "secret");
        assertEquals("https://ollama.qa.guru", target.base());
        assertEquals(basic("qaguru", "secret"), target.authorization());
    }

    @Test
    void userinfoInUrlIsStrippedAndSentAsHeader() {
        OllamaClient.Target target = OllamaClient.resolve("https://qaguru:secret@ollama.qa.guru", null, null);
        assertEquals("https://ollama.qa.guru", target.base());
        assertEquals(basic("qaguru", "secret"), target.authorization());
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
