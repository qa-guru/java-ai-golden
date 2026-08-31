package eval.generation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Tag("eval")
@Tag("live")
@EnabledIfSystemProperty(named = "live", matches = "true")
@Timeout(value = 4, unit = TimeUnit.MINUTES)
@DisplayName("Live generation")
class LiveGenerationContractTest {

    static Stream<GoldenCase> golden() {
        return GoldenReader.read();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("golden")
    @DisplayName("execute workflow then check golden")
    void executeThenCheck(GoldenCase row) throws Exception {
        String out = OllamaClient.chat(WorkflowPrompt.system(row), row.prompt());
        if ("true".equals(System.getProperty("writeFixtures"))) {
            var path = GoldenReader.evalDir().resolve("fixtures").resolve(row.id() + ".out.md");
            Files.writeString(path, out, StandardCharsets.UTF_8);
        }
        ContractAssertions.assertMatches(row, out);
    }
}
