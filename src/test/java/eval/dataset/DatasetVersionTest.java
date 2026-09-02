package eval.dataset;

import eval.generation.GoldenCase;
import eval.generation.GoldenReader;
import eval.pack.PackFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Dataset version and stable ids")
class DatasetVersionTest {

    @Test
    void generationDatasetIsVersioned() {
        DatasetManifest manifest = GoldenReader.manifest();
        assertEquals("generation-v1", manifest.version());
        assertEquals("generation-v1", GoldenReader.datasetVersion());
    }

    @Test
    void packDatasetIsVersioned() {
        assertEquals("pack-v1", PackFiles.datasetVersion());
        assertEquals("pack-v1", PackFiles.manifest().version());
    }

    @Test
    void everyCaseIdIsStableAndUnique() {
        List<GoldenCase> rows = GoldenReader.loadAll();
        assertEquals(8, rows.size());
        HashSet<String> ids = new HashSet<>();
        for (GoldenCase row : rows) {
            assertTrue(ids.add(row.id()), row.id());
            assertTrue(row.id().matches("[a-z0-9-]+"), row.id());
        }
    }

    @Test
    void duplicateIdsAreRejected() {
        GoldenCase a = GoldenReader.require("jailbreak-env");
        assertThrows(IllegalStateException.class, () -> GoldenReader.requireUniqueIds(List.of(a, a)));
    }
}
