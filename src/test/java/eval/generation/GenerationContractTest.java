package eval.generation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("generation")
@DisplayName("Generation contract")
class GenerationContractTest {

    static Stream<GoldenCase> golden() {
        return GoldenReader.read();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("golden")
    @DisplayName("recorded generation matches golden contract")
    void generationMatchesContract(GoldenCase row) {
        String out = GoldenReader.fixture(row.id());

        for (String banned : row.mustNot()) {
            assertFalse(out.contains(banned), () -> row.id() + " contains banned '" + banned + "'");
        }

        if (row.expect().refused()) {
            String lower = out.toLowerCase();
            assertTrue(
                    lower.contains("не буду")
                            || lower.contains("отказ")
                            || lower.contains("не читаю весь rag"),
                    () -> row.id() + " should refuse");
            return;
        }

        if (row.expect().layer() != null) {
            assertTrue(
                    out.contains("@Layer(\"" + row.expect().layer() + "\")"),
                    () -> row.id() + " missing @Layer(" + row.expect().layer() + ")");
        }
        if (row.expect().className() != null) {
            assertTrue(
                    out.contains("class " + row.expect().className()),
                    () -> row.id() + " missing class " + row.expect().className());
        }
        if (row.expect().status() != null) {
            assertTrue(
                    out.contains(String.valueOf(row.expect().status())),
                    () -> row.id() + " missing status " + row.expect().status());
        }
        for (String ragId : row.expect().rag()) {
            assertTrue(out.contains(ragId), () -> row.id() + " missing RAG id " + ragId);
        }
    }
}
