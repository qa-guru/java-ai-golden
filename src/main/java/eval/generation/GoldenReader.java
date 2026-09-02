package eval.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import eval.dataset.DatasetManifest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class GoldenReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GOLDEN_FILE = "golden-generation.jsonl";
    private static final String MANIFEST_FILE = "dataset.json";

    private GoldenReader() {
    }

    public static Stream<GoldenCase> read() {
        return loadAll().stream();
    }

    public static List<GoldenCase> loadAll() {
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
        requireUniqueIds(rows);
        return List.copyOf(rows);
    }

    public static DatasetManifest manifest() {
        Path path = evalDir().resolve(MANIFEST_FILE);
        try {
            return MAPPER.readValue(Files.readString(path, StandardCharsets.UTF_8), DatasetManifest.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing or invalid " + MANIFEST_FILE + ": " + path, e);
        }
    }

    public static String datasetVersion() {
        return manifest().version();
    }

    public static GoldenCase require(String id) {
        return loadAll().stream()
                .filter(c -> id.equals(c.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing golden " + id));
    }

    public static String fixture(String id) {
        Path path = evalDir().resolve("fixtures").resolve(id + ".out.md");
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No fixture: " + path, e);
        }
    }

    public static Path evalDir() {
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

    public static void requireUniqueIds(List<GoldenCase> rows) {
        Set<String> seen = new HashSet<>();
        for (GoldenCase row : rows) {
            if (!seen.add(row.id())) {
                throw new IllegalStateException("duplicate golden case id: " + row.id());
            }
        }
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
