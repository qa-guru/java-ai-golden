package eval.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import eval.dataset.DatasetIdentity;
import eval.dataset.DatasetManifest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
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
    static final String CLASSPATH_DIR = "/eval/generation";
    private static final String SOURCE_DIR = "src/main/resources/eval/generation";

    private GoldenReader() {
    }

    public static Stream<GoldenCase> read() {
        return loadAll().stream();
    }

    public static List<GoldenCase> loadAll() {
        return parseLines(readResourceLines(CLASSPATH_DIR + "/" + GOLDEN_FILE));
    }

    public static List<GoldenCase> loadHoldout() {
        return parseLines(readResourceLines(CLASSPATH_DIR + "/holdout/golden-holdout.jsonl"));
    }

    public static List<GoldenCase> loadSplit(String split) {
        if (split == null || split.isBlank() || split.equalsIgnoreCase("development")) {
            return loadAll();
        }
        if (split.equalsIgnoreCase("holdout")) {
            return loadHoldout();
        }
        throw new IllegalArgumentException("unknown --split=" + split + " (expected development|holdout)");
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

    public static DatasetManifest manifest() {
        return readManifest(CLASSPATH_DIR + "/" + MANIFEST_FILE);
    }

    public static DatasetManifest holdoutManifest() {
        return readManifest(CLASSPATH_DIR + "/holdout/" + MANIFEST_FILE);
    }

    public static String datasetVersion() {
        return manifest().version();
    }

    public static String datasetVersion(String split) {
        if (split == null || split.isBlank() || split.equalsIgnoreCase("development")) {
            return datasetVersion();
        }
        if (split.equalsIgnoreCase("holdout")) {
            return holdoutManifest().version();
        }
        throw new IllegalArgumentException("unknown --split=" + split + " (expected development|holdout)");
    }

    public static GoldenCase require(String id) {
        return loadAll().stream()
                .filter(c -> id.equals(c.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing golden " + id));
    }

    public static String fixture(String id) {
        String primary = CLASSPATH_DIR + "/fixtures/" + id + ".out.md";
        String holdout = CLASSPATH_DIR + "/holdout/fixtures/" + id + ".out.md";
        if (GoldenReader.class.getResource(primary) != null) {
            return readResource(primary);
        }
        if (GoldenReader.class.getResource(holdout) != null) {
            return readResource(holdout);
        }
        throw new IllegalStateException("No fixture: " + primary + " or " + holdout);
    }

    /**
     * Classpath directory of the generation dataset (Gradle exploded resources).
     */
    public static Path evalDir() {
        Path fromClasspath = dirFromResource(CLASSPATH_DIR + "/" + GOLDEN_FILE);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        Path src = sourceEvalDirOrNull();
        if (src != null) {
            return src;
        }
        throw new IllegalStateException(
                "Missing " + GOLDEN_FILE + " (cwd=" + Path.of("").toAbsolutePath() + ")");
    }

    /**
     * Source-tree directory for {@code -DwriteFixtures=true}. Falls back to {@link #evalDir()}.
     */
    public static Path writableEvalDir() {
        Path src = sourceEvalDirOrNull();
        return src != null ? src : evalDir();
    }

    public static void requireUniqueIds(List<GoldenCase> rows) {
        DatasetIdentity.validate(rows);
    }

    private static Path sourceEvalDirOrNull() {
        Path src = Path.of(SOURCE_DIR).toAbsolutePath().normalize();
        if (Files.isRegularFile(src.resolve(GOLDEN_FILE))) {
            return src;
        }
        return null;
    }

    private static DatasetManifest readManifest(String resource) {
        try {
            return MAPPER.readValue(readResource(resource), DatasetManifest.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing or invalid " + resource, e);
        }
    }

    private static List<String> readResourceLines(String resource) {
        return readResource(resource).lines().toList();
    }

    private static String readResource(String resource) {
        try (InputStream in = GoldenReader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path dirFromResource(String resource) {
        URL url = GoldenReader.class.getResource(resource);
        if (url == null || !"file".equalsIgnoreCase(url.getProtocol())) {
            return null;
        }
        try {
            return Path.of(url.toURI()).getParent();
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
