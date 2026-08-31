package eval.pack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class PackFiles {

    private static final Pattern YAML_ID = Pattern.compile("(?m)^id:\\s*(\\S+)");

    private PackFiles() {
    }

    static Path root() {
        Path cwd = Path.of("src/test/resources/pack").toAbsolutePath().normalize();
        if (Files.isDirectory(cwd.resolve("rag"))) {
            return cwd;
        }
        throw new IllegalStateException("Missing pack/rag (cwd=" + Path.of("").toAbsolutePath() + ")");
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
}
