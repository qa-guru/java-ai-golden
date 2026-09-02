package eval.execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class GitMetadata {

    private GitMetadata() {
    }

    public static String shortCommit() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            boolean done = p.waitFor(3, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return "unknown";
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return out.isBlank() || p.exitValue() != 0 ? "unknown" : out;
        } catch (IOException | InterruptedException e) {
            return "unknown";
        }
    }
}
