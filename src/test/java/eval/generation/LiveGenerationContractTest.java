package eval.generation;

import eval.execution.EvalConfig;
import eval.provider.ModelRunner;
import eval.provider.ModelRunners;
import org.junit.jupiter.api.Assumptions;
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
        EvalConfig config = EvalConfig.resolve(new String[]{"--mode=live"});
        if (row.expect().isRed()) {
            Assumptions.assumeTrue(config.includeRed(), "red 7b rows: -Dred=true");
        }
        var built = WorkflowPrompt.build(row);
        ModelRunner runner = ModelRunners.create(config);
        String raw = runner.complete(built.system(), row.prompt(), config.model()).content();
        Path dir = Path.of("build/live-out");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(row.id() + ".out.md"), raw, StandardCharsets.UTF_8);
        if (!built.retrieved().isEmpty()) {
            System.out.println("===== RETRIEVE " + row.id() + " =====\n" + String.join(", ", built.retrieved()));
        }
        System.out.println(
                "===== LIVE " + row.id() + " provider=" + config.provider()
                        + " model=" + config.model() + " =====\n" + raw
                        + "\n===== END " + row.id() + " =====");
        if (config.writeFixtures()) {
            var path = GoldenReader.writableEvalDir().resolve("fixtures").resolve(row.id() + ".out.md");
            Files.writeString(path, raw, StandardCharsets.UTF_8);
        }
        ContractAssertions.assertMatches(row, raw);
        if (row.expect().refused() || !config.judgeEnabled()) {
            return;
        }
        String judged = Judge.review(row, raw, built.retrieved(), runner, config.judgeModel());
        Files.writeString(dir.resolve(row.id() + ".judge.md"), judged, StandardCharsets.UTF_8);
        System.out.println("===== JUDGE " + row.id() + " =====\n" + judged + "\n===== END JUDGE " + row.id() + " =====");
        System.out.println(
                "===== JUDGE verdict " + row.id() + " " + Judge.parse(judged)
                        + " (REJECTED/PENDING does not fail live) =====");
    }
}
