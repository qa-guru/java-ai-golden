package eval.pack;

import eval.generation.GoldenReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("pack")
@DisplayName("Pack diet inventory")
class PackDietTest {

    @Test
    @DisplayName("every golden RAG id has a chunk file")
    void goldenRagIdsExist() {
        GoldenReader.read()
                .filter(row -> !row.expect().refused())
                .forEach(row -> {
                    for (String id : row.expect().rag()) {
                        assertTrue(
                                Files.isRegularFile(PackFiles.ragDir().resolve(id + ".md")),
                                row.id() + " missing pack/rag/" + id + ".md");
                    }
                });
    }

    @Test
    @DisplayName("non-refuse golden retrieves 2–4 chunks")
    void goldenRagCountIsTwoToFour() {
        GoldenReader.read()
                .filter(row -> !row.expect().refused())
                .forEach(row -> {
                    int n = row.expect().rag().size();
                    assertTrue(n >= 2 && n <= 4, row.id() + " rag size " + n + " (want 2–4)");
                });
    }

    @Test
    @DisplayName("filename stem matches YAML id")
    void filenameMatchesFrontmatterId() {
        for (Path file : PackFiles.ragFiles()) {
            String stem = file.getFileName().toString().replaceFirst("\\.md$", "");
            String markdown = PackFiles.rag(stem);
            assertEquals(stem, PackFiles.frontmatterId(markdown), file.getFileName().toString());
        }
    }

    @Test
    @DisplayName("skill, rules, ADR 009, login PO context exist")
    void requiredPackFilesExist() {
        for (String relative : List.of(
                "rules.md",
                "qa-write-test.md",
                "adr/009-login-401-is-api.md",
                "context/login-po.md")) {
            assertTrue(Files.isRegularFile(PackFiles.root().resolve(relative)), relative);
        }
    }
}
