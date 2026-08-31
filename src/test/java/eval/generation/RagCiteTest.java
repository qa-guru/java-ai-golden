package eval.generation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("generation")
@DisplayName("Retriever owns RAG citation")
class RagCiteTest {

    @Test
    void replacesSubsetCitationWithRetrieverHeader() {
        String out = RagCite.withRetrieverHeader(
                "RAG: test-api-layer\n\nclass AuthApiTests {}",
                List.of("test-api-layer", "test-layers"));
        assertTrue(out.startsWith("RAG: test-api-layer, test-layers\n"));
        assertTrue(out.contains("class AuthApiTests"));
    }

    @Test
    void refuseHasNoHeader() {
        assertEquals("Отказ.", RagCite.withRetrieverHeader("Отказ.", List.of()));
    }
}
