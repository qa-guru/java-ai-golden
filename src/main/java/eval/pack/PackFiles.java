package eval.pack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import eval.dataset.DatasetManifest;

public final class PackFiles {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern YAML_ID = Pattern.compile("(?m)^id:\\s*(\\S+)");
    private static final Pattern FRONTMATTER = Pattern.compile("(?s)^---\\r?\\n(.*?)\\r?\\n---");
    private static final Pattern HEADING = Pattern.compile("(?m)^#\\s+(.+)$");
    private static final Pattern YAML_LIST = Pattern.compile("(?m)^%s:\\s*\\[(.*)\\]\\s*$");

    private PackFiles() {
    }

    static Path root() {
        Path cwd = Path.of("src/test/resources/pack").toAbsolutePath().normalize();
        if (Files.isDirectory(cwd.resolve("rag"))) {
            return cwd;
        }
        throw new IllegalStateException("Missing pack/rag (cwd=" + Path.of("").toAbsolutePath() + ")");
    }

    public static DatasetManifest manifest() {
        Path path = root().resolve("dataset.json");
        try {
            return MAPPER.readValue(Files.readString(path, StandardCharsets.UTF_8), DatasetManifest.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing or invalid pack dataset.json: " + path, e);
        }
    }

    public static String datasetVersion() {
        return manifest().version();
    }

    static Path ragDir() {
        return root().resolve("rag");
    }

    static String read(String relative) {
        Path path = root().resolve(relative);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing pack file: " + path, e);
        }
    }

    static String rag(String id) {
        return read("rag/" + id + ".md");
    }

    static String diet(List<String> ids) {
        StringBuilder out = new StringBuilder();
        for (String id : ids) {
            out.append(rag(id)).append('\n');
        }
        return out.toString();
    }

    static List<Path> ragFiles() {
        try (Stream<Path> stream = Files.list(ragDir())) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".md")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String frontmatterId(String markdown) {
        Matcher yaml = YAML_ID.matcher(markdown);
        if (yaml.find()) {
            return yaml.group(1).strip();
        }
        throw new IllegalStateException("RAG chunk missing YAML id:");
    }

    public static List<String> chunkIds() {
        return chunks().stream().map(RagChunk::id).toList();
    }

    static List<RagChunk> chunks() {
        List<RagChunk> out = new ArrayList<>();
        for (Path file : ragFiles()) {
            String stem = file.getFileName().toString().replaceFirst("\\.md$", "");
            out.add(parseChunk(stem, rag(stem)));
        }
        return out;
    }

    static RagChunk parseChunk(String fallbackId, String markdown) {
        String fm = frontmatter(markdown);
        String id = frontmatterId(markdown);
        if (id.isBlank()) {
            id = fallbackId;
        }
        String heading = "";
        Matcher head = HEADING.matcher(markdown);
        if (head.find()) {
            heading = head.group(1).strip();
        }
        return new RagChunk(
                id,
                yamlList(fm, "tags"),
                yamlList(fm, "related"),
                yamlList(fm, "index"),
                heading);
    }

    private static String frontmatter(String markdown) {
        Matcher m = FRONTMATTER.matcher(markdown);
        return m.find() ? m.group(1) : "";
    }

    private static List<String> yamlList(String frontmatter, String key) {
        Matcher m = Pattern.compile(String.format(YAML_LIST.pattern(), Pattern.quote(key)))
                .matcher(frontmatter);
        if (!m.find()) {
            return List.of();
        }
        String inner = m.group(1).strip();
        if (inner.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : inner.split(",")) {
            String v = part.strip().replaceAll("^['\"]|['\"]$", "");
            if (!v.isBlank()) {
                values.add(v);
            }
        }
        return List.copyOf(values);
    }
}
