package eval.generation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

final class GoldenReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GOLDEN_FILE = "golden-generation.jsonl";

    private GoldenReader() {
    }

    static Stream<GoldenCase> read() {
        List<GoldenCase> rows = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(evalDir().resolve(GOLDEN_FILE), StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                rows.add(MAPPER.readValue(line, GoldenCase.class));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows.stream();
    }

    static String fixture(String id) {
        Path path = evalDir().resolve("fixtures").resolve(id + ".out.md");
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No fixture: " + path, e);
        }
    }

    static Path evalDir() {
        Path cwdCandidate = Path.of("src/test/java/eval/generation").toAbsolutePath().normalize();
        if (Files.isRegularFile(cwdCandidate.resolve(GOLDEN_FILE))) {
            return cwdCandidate;
        }
        Path fromClasses = evalDirFromClasses();
        if (fromClasses != null) {
            return fromClasses;
        }
        throw new IllegalStateException(
                "Missing " + GOLDEN_FILE + " (cwd=" + Path.of("").toAbsolutePath() + ")");
    }

    private static Path evalDirFromClasses() {
        var codeSource = GoldenReader.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }
        try {
            Path cursor = Path.of(codeSource.getLocation().toURI());
            for (int i = 0; i < 8 && cursor != null; i++) {
                Path candidate = cursor.resolve("src/test/java/eval/generation").resolve(GOLDEN_FILE);
                if (Files.isRegularFile(candidate)) {
                    return candidate.getParent();
                }
                cursor = cursor.getParent();
            }
        } catch (URISyntaxException e) {
            return null;
        }
        return null;
    }
}
