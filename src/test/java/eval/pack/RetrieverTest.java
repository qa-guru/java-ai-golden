package eval.pack;

import eval.generation.GoldenCase;
import eval.generation.GoldenReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("pack")
@DisplayName("Lexical retriever")
class RetrieverTest {

    static Stream<GoldenCase> generateRows() {
        return GoldenReader.read().filter(row -> !row.expect().refused());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generateRows")
    @DisplayName("retrieve set matches golden expect.rag")
    void retrieveMatchesGolden(GoldenCase row) {
        List<String> got = LexicalRetriever.retrieve(row.prompt());
        Set<String> expected = new HashSet<>(row.expect().rag());
        assertEquals(
                expected,
                new HashSet<>(got),
                () -> row.id() + " rank=" + LexicalRetriever.rank(row.prompt()) + " got=" + got);
        assertTrue(got.size() >= LexicalRetriever.MIN && got.size() <= LexicalRetriever.MAX, got.toString());
    }

    @Test
    @DisplayName("успешного and успешный stem to the same token")
    void russianStemAlignsHappyPrompt() {
        assertEquals(LexicalRetriever.stem("успешный"), LexicalRetriever.stem("успешного"));
        assertEquals(LexicalRetriever.stem("пароль"), LexicalRetriever.stem("паролем"));
        assertEquals(LexicalRetriever.stem("неправильный"), LexicalRetriever.stem("неправильным"));
    }

    @Test
    @DisplayName("Не e2e / Не api are layer bans, not stopwords")
    void layerNegation() {
        assertEquals(Set.of("e2e"), LexicalRetriever.bannedLayers("JSON 401. Не e2e, не клики."));
        assertEquals(Set.of("api"), LexicalRetriever.bannedLayers("LoginPage. Не api."));
    }

    @Test
    @DisplayName("API retrieve diet has no form-negative chain")
    void apiRetrieveDoesNotLeakFormNegative() {
        GoldenCase row = row("login-401-api");
        String diet = PackFiles.diet(LexicalRetriever.retrieve(row.prompt()));
        assertFalse(diet.contains("submitExpectingError"), diet);
        assertFalse(diet.contains("fillAndSubmitForm"), diet);
        assertTrue(diet.contains("401") || diet.contains("Wrong login or password"), diet);
    }

    @Test
    @DisplayName("polluting po-fluent index steals the form-negative query")
    void pollutingHappyIndexStealsFormNegativeQuery() {
        String prompt = row("login-401-ui").prompt();
        assertFalse(
                LexicalRetriever.retrieve(prompt).contains("po-fluent"),
                "clean happy chunk must not enter UI diet");

        List<RagChunk> poisoned = new ArrayList<>();
        for (RagChunk chunk : PackFiles.chunks()) {
            if ("po-fluent".equals(chunk.id())) {
                List<String> index = new ArrayList<>(chunk.index());
                index.addAll(List.of("неуспешный", "неправильный", "пароль", "логин"));
                poisoned.add(chunk.withIndex(index));
            } else {
                poisoned.add(chunk);
            }
        }
        List<String> stolen = LexicalRetriever.retrieve(prompt, poisoned);
        assertTrue(
                stolen.contains("po-fluent"),
                "polluted po-fluent should enter UI diet, got " + stolen
                        + " rank=" + LexicalRetriever.rank(prompt, poisoned));
    }

    private static GoldenCase row(String id) {
        return GoldenReader.read()
                .filter(c -> id.equals(c.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing golden " + id));
    }
}
