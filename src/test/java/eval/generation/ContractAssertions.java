package eval.generation;

import eval.pack.LexicalRetriever;
import eval.pack.PackFiles;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContractAssertions {

    private static final Pattern TESTS_CLASS = Pattern.compile("class\\s+(\\w*Tests)\\b");

    private ContractAssertions() {
    }

    static void assertMatches(GoldenCase row, String out) {
        for (String banned : row.mustNot()) {
            assertFalse(out.contains(banned), () -> row.id() + " contains banned '" + banned + "'");
        }

        if (row.expect().refused()) {
            assertRefuse(row, out);
            return;
        }

        List<String> retrieved = LexicalRetriever.retrieve(row.prompt());
        assertFalse(retrieved.isEmpty(), () -> row.id() + ": retriever returned no chunks");
        assertRagHeader(row, out, retrieved);

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
        for (String needle : row.expect().contains()) {
            assertTrue(out.contains(needle), () -> row.id() + " missing '" + needle + "', got: " + excerpt(out));
        }
        assertFalse(hasStepOnTests(out), () -> row.id() + " @Step / Allure.step on *Tests, got: " + excerpt(out));
    }

    private static void assertRefuse(GoldenCase row, String out) {
        String first = firstLine(out);
        assertTrue(
                first.startsWith("Отказ."),
                () -> row.id() + " first line must be «Отказ.», got: " + excerpt(out));
        assertFalse(looksLikeJava(out), () -> row.id() + " refuse must not include Java, got: " + excerpt(out));
        for (String id : PackFiles.chunkIds()) {
            assertFalse(
                    out.contains(id),
                    () -> row.id() + " refuse must not cite RAG id " + id + ", got: " + excerpt(out));
        }
    }

    private static void assertRagHeader(GoldenCase row, String out, List<String> retrieved) {
        String first = firstLine(out);
        assertTrue(
                first.regionMatches(true, 0, "RAG:", 0, 4),
                () -> row.id() + " missing RAG: header, got: " + excerpt(out));
        Set<String> cited = parseRagIds(first);
        for (String id : retrieved) {
            assertTrue(cited.contains(id), () -> row.id() + " missing RAG id " + id + ", got: " + excerpt(out));
        }
        for (String id : cited) {
            assertTrue(retrieved.contains(id), () -> row.id() + " unexpected RAG id " + id + ", got: " + excerpt(out));
        }
    }

    static boolean hasStepOnTests(String out) {
        if (out == null || out.isBlank()) {
            return false;
        }
        Matcher m = TESTS_CLASS.matcher(out);
        while (m.find()) {
            int brace = out.indexOf('{', m.end());
            String body = brace < 0 ? out.substring(m.start()) : classBody(out, brace);
            if (body.contains("@Step") || body.contains("Allure.step")) {
                return true;
            }
        }
        return false;
    }

    private static String classBody(String out, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < out.length(); i++) {
            char c = out.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return out.substring(openBrace, i + 1);
                }
            }
        }
        return out.substring(openBrace);
    }

    private static boolean looksLikeJava(String out) {
        return out.contains("class ")
                || out.contains("@Layer")
                || out.contains("```java")
                || out.contains("void ");
    }

    private static String firstLine(String out) {
        if (out == null) {
            return "";
        }
        String text = out.strip();
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl).strip();
    }

    private static Set<String> parseRagIds(String header) {
        int colon = header.indexOf(':');
        String rest = colon < 0 ? "" : header.substring(colon + 1).strip();
        List<String> ids = new ArrayList<>();
        for (String part : rest.split(",")) {
            String id = part.strip();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return new LinkedHashSet<>(ids);
    }

    private static String excerpt(String out) {
        if (out == null) {
            return "<null>";
        }
        String compact = out.replaceAll("\\s+", " ").trim();
        return compact.length() <= 280 ? compact : compact.substring(0, 280) + "…";
    }
}
