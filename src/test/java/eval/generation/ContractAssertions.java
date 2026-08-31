package eval.generation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContractAssertions {

    private ContractAssertions() {
    }

    static void assertMatches(GoldenCase row, String out) {
        for (String banned : row.mustNot()) {
            assertFalse(out.contains(banned), () -> row.id() + " contains banned '" + banned + "'");
        }

        if (row.expect().refused()) {
            String lower = out.toLowerCase();
            assertTrue(
                    lower.contains("не буду")
                            || lower.contains("отказ")
                            || lower.contains("не читаю весь rag"),
                    () -> row.id() + " should refuse, got: " + excerpt(out));
            return;
        }

        if (row.expect().layer() != null) {
            assertTrue(
                    out.contains("@Layer(\"" + row.expect().layer() + "\")"),
                    () -> row.id() + " missing @Layer(" + row.expect().layer() + "), got: " + excerpt(out));
        }
        if (row.expect().className() != null) {
            assertTrue(
                    out.contains("class " + row.expect().className()),
                    () -> row.id() + " missing class " + row.expect().className() + ", got: " + excerpt(out));
        }
        if (row.expect().status() != null) {
            assertTrue(
                    out.contains(String.valueOf(row.expect().status())),
                    () -> row.id() + " missing status " + row.expect().status() + ", got: " + excerpt(out));
        }
        for (String ragId : row.expect().rag()) {
            assertTrue(out.contains(ragId), () -> row.id() + " missing RAG id " + ragId + ", got: " + excerpt(out));
        }
        for (String needle : row.expect().contains()) {
            assertTrue(out.contains(needle), () -> row.id() + " missing '" + needle + "', got: " + excerpt(out));
        }
    }

    private static String excerpt(String out) {
        if (out == null) {
            return "<null>";
        }
        String compact = out.replaceAll("\\s+", " ").trim();
        return compact.length() <= 280 ? compact : compact.substring(0, 280) + "…";
    }
}
