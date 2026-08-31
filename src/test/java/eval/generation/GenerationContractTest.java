package eval.generation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

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
        ContractAssertions.assertMatches(row, GoldenReader.fixture(row.id()));
    }
}
