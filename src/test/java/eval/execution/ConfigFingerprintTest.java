package eval.execution;

import eval.domain.EvalRun;
import eval.domain.RunConfiguration;
import eval.generation.GoldenReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("eval")
@Tag("framework")
@DisplayName("Configuration fingerprint")
class ConfigFingerprintTest {

    @Test
    void sameInputsSameFingerprintAndOutputDirIsIgnored() {
        RunConfiguration a = new RunConfiguration(
                "DETERMINISTIC", "m", "j", false, 1, false, "FAILURE", "build/eval", "ollama", null, "development");
        RunConfiguration b = new RunConfiguration(
                "DETERMINISTIC", "m", "j", false, 1, false, "ALWAYS", "elsewhere", "ollama", null, "development");
        String ha = ConfigFingerprint.of(a, "generation-v1", "hash", "pack-v1", null, "abc");
        String hb = ConfigFingerprint.of(b, "generation-v1", "hash", "pack-v1", null, "abc");
        assertEquals(ha, hb);
    }

    @Test
    void modelChangeChangesFingerprint() {
        RunConfiguration a = new RunConfiguration(
                "LIVE", "qwen-a", "j", true, 1, false, "FAILURE", "build", "ollama");
        RunConfiguration b = new RunConfiguration(
                "LIVE", "qwen-b", "j", true, 1, false, "FAILURE", "build", "ollama");
        assertNotEquals(
                ConfigFingerprint.of(a, "generation-v1", "h", "pack-v1", "exp-1", "abc"),
                ConfigFingerprint.of(b, "generation-v1", "h", "pack-v1", "exp-1", "abc"));
    }

    @Test
    void deterministicRunCarriesFingerprint() {
        EvalRun run = new EvalExecutor(EvalConfig.resolve(new String[]{
                "--mode=deterministic", "--artifacts=never", "--output=build/eval-fp-test"
        })).execute();
        assertEquals(64, run.configFingerprint().length());
        assertEquals(GoldenReader.datasetHash(), run.datasetHash());
    }
}
