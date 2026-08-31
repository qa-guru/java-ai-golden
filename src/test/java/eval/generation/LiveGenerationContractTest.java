package eval.generation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Tag("eval")
@Tag("live")
@EnabledIfSystemProperty(named = "live", matches = "true")
@Timeout(value = 8, unit = TimeUnit.MINUTES)
@DisplayName("Live generation")
class LiveGenerationContractTest {

    static Stream<GoldenCase> golden() {
        return GoldenReader.read();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("golden")
    @DisplayName("execute workflow, contract, then judge")
    void executeThenCheck(GoldenCase row) throws Exception {
        var built = WorkflowPrompt.build(row);
        String out = OllamaClient.chat(built.system(), row.prompt());
        Path dir = Path.of("build/live-out");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(row.id() + ".out.md"), out, StandardCharsets.UTF_8);
        if (!built.retrieved().isEmpty()) {
            System.out.println("===== RETRIEVE " + row.id() + " =====\n" + String.join(", ", built.retrieved()));
        }
        System.out.println("===== LIVE " + row.id() + " =====\n" + out + "\n===== END " + row.id() + " =====");
        if ("true".equals(System.getProperty("writeFixtures"))) {
            var path = GoldenReader.evalDir().resolve("fixtures").resolve(row.id() + ".out.md");
            Files.writeString(path, out, StandardCharsets.UTF_8);
        }
        ContractAssertions.assertMatches(row, out);
        if (row.expect().refused() || "false".equals(System.getProperty("judge", "true"))) {
            return;
        }
        String judged = Judge.review(row, out);
        Files.writeString(dir.resolve(row.id() + ".judge.md"), judged, StandardCharsets.UTF_8);
        System.out.println("===== JUDGE " + row.id() + " =====\n" + judged + "\n===== END JUDGE " + row.id() + " =====");
        Judge.assertAccepted(row, judged);
    }
}
