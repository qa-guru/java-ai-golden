package eval.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import eval.dataset.DatasetIdentity;
import eval.dataset.DatasetManifest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        return loadFile(evalDir().resolve(GOLDEN_FILE));
    }

    public static List<GoldenCase> loadHoldout() {
        return loadFile(evalDir().resolve("holdout").resolve("golden-holdout.jsonl"));
    }

    public static List<GoldenCase> loadSplit(String split) {
        if (split != null && split.equalsIgnoreCase("holdout")) {
            return loadHoldout();
        }
        return loadAll();
    }

    public static String datasetHash() {
        return DatasetIdentity.hash(loadAll());
    }

    public static String datasetHash(List<GoldenCase> rows) {
        return DatasetIdentity.hash(rows);
    }

    public static List<GoldenCase> parseLines(List<String> lines) {
        List<GoldenCase> rows = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank() || line.startsWith("#")) {
                continue;
            }
            try {
                rows.add(MAPPER.readValue(line, GoldenCase.class));
            } catch (Exception e) {
                throw new IllegalStateException("invalid golden JSONL: " + e.getMessage(), e);
            }
        }
        DatasetIdentity.validate(rows);
        return List.copyOf(rows);
    }

    static List<GoldenCase> loadFile(Path path) {
        try {
            return parseLines(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static DatasetManifest manifest() {
        Path path = evalDir().resolve(MANIFEST_FILE);
        try {
            return MAPPER.readValue(Files.readString(path, StandardCharsets.UTF_8), DatasetManifest.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing or invalid " + MANIFEST_FILE + ": " + path, e);
        }
    }

    public static DatasetManifest holdoutManifest() {
        Path path = evalDir().resolve("holdout").resolve(MANIFEST_FILE);
        try {
            return MAPPER.readValue(Files.readString(path, StandardCharsets.UTF_8), DatasetManifest.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing or invalid holdout " + MANIFEST_FILE + ": " + path, e);
        }
    }

    public static String datasetVersion() {
        return manifest().version();
    }

    public static String datasetVersion(String split) {
        if (split != null && split.equalsIgnoreCase("holdout")) {
            return holdoutManifest().version();
        }
        return datasetVersion();
    }

    public static GoldenCase require(String id) {
        return loadAll().stream()
                .filter(c -> id.equals(c.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing golden " + id));
    }

    public static String fixture(String id) {
        Path primary = evalDir().resolve("fixtures").resolve(id + ".out.md");
        Path holdout = evalDir().resolve("holdout").resolve("fixtures").resolve(id + ".out.md");
        Path path = Files.isRegularFile(primary) ? primary : holdout;
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No fixture: " + primary + " or " + holdout, e);
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
        DatasetIdentity.validate(rows);
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
