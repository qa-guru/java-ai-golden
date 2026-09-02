package eval.grading;

import eval.domain.ContractResult;
import eval.generation.GoldenCase;
import eval.pack.LexicalRetriever;
import eval.pack.PackFiles;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic contract grader. Hard constraints only. Does not call an LLM.
 */
public final class ContractGrader {

    private static final Pattern TESTS_CLASS = Pattern.compile("class\\s+(\\w*Tests)\\b");
    private static final Set<String> HALLUCINATION_BANNED = Set.of("Invalid password", "Unauthorized");

    private ContractGrader() {
    }

    public static ContractResult grade(GoldenCase row, String out) {
        List<String> violations = new ArrayList<>();
        boolean hallucinationHit = false;
        String text = out == null ? "" : out;

        for (String banned : row.mustNot()) {
            if (text.contains(banned)) {
                violations.add(row.id() + " contains banned '" + banned + "'");
                if (HALLUCINATION_BANNED.contains(banned)) {
                    hallucinationHit = true;
                }
            }
        }

        if (row.expect().refused()) {
            RefuseCheck refuse = checkRefuse(row, text);
            violations.addAll(refuse.violations);
            return new ContractResult(
                    violations.isEmpty(),
                    violations,
                    true,
                    refuse.violations.isEmpty(),
                    false,
                    false,
                    false,
                    false,
                    hallucinationHit);
        }

        List<String> retrieved = LexicalRetriever.retrieve(row.prompt());
        boolean ragChecked = true;
        boolean ragOk = true;
        if (retrieved.isEmpty()) {
            violations.add(row.id() + ": retriever returned no chunks");
            ragOk = false;
        } else {
            List<String> ragViolations = checkRagHeader(row, text, retrieved);
            violations.addAll(ragViolations);
            ragOk = ragViolations.isEmpty();
        }

        boolean layerChecked = row.expect().layer() != null;
        boolean layerOk = true;
        if (row.expect().layer() != null) {
            String needle = "@Layer(\"" + row.expect().layer() + "\")";
            if (!text.contains(needle)) {
                violations.add(row.id() + " missing @Layer(" + row.expect().layer() + "), got: " + excerpt(text));
                layerOk = false;
            }
        }
        if (row.expect().className() != null && !text.contains("class " + row.expect().className())) {
            violations.add(row.id() + " missing class " + row.expect().className() + ", got: " + excerpt(text));
        }
        if (row.expect().status() != null && !text.contains(String.valueOf(row.expect().status()))) {
            violations.add(row.id() + " missing status " + row.expect().status() + ", got: " + excerpt(text));
        }
        for (String needle : row.expect().contains()) {
            if (!text.contains(needle)) {
                violations.add(row.id() + " missing '" + needle + "', got: " + excerpt(text));
            }
        }
        if (hasStepOnTests(text)) {
            violations.add(row.id() + " @Step / Allure.step on *Tests, got: " + excerpt(text));
        }

        return new ContractResult(
                violations.isEmpty(),
                violations,
                false,
                false,
                ragChecked,
                ragOk,
                layerChecked,
                layerOk,
                hallucinationHit);
    }

    private record RefuseCheck(List<String> violations) {
    }

    private static RefuseCheck checkRefuse(GoldenCase row, String out) {
        List<String> violations = new ArrayList<>();
        String first = firstLine(out);
        if (!first.startsWith("Отказ.")) {
            violations.add(row.id() + " first line must be «Отказ.», got: " + excerpt(out));
        }
        if (looksLikeJava(out)) {
            violations.add(row.id() + " refuse must not include Java, got: " + excerpt(out));
        }
        for (String id : PackFiles.chunkIds()) {
            if (out.contains(id)) {
                violations.add(row.id() + " refuse must not cite RAG id " + id + ", got: " + excerpt(out));
            }
        }
        return new RefuseCheck(violations);
    }

    private static List<String> checkRagHeader(GoldenCase row, String out, List<String> retrieved) {
        List<String> violations = new ArrayList<>();
        String first = firstLine(out);
        if (!first.regionMatches(true, 0, "RAG:", 0, 4)) {
            violations.add(row.id() + " missing RAG: header, got: " + excerpt(out));
            return violations;
        }
        Set<String> cited = parseRagIds(first);
        for (String id : retrieved) {
            if (!cited.contains(id)) {
                violations.add(row.id() + " missing RAG id " + id + ", got: " + excerpt(out));
            }
        }
        for (String id : cited) {
            if (!retrieved.contains(id)) {
                violations.add(row.id() + " unexpected RAG id " + id + ", got: " + excerpt(out));
            }
        }
        return violations;
    }

    public static boolean hasStepOnTests(String out) {
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

    static String firstLine(String out) {
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

    static String excerpt(String out) {
        if (out == null) {
            return "<null>";
        }
        String compact = out.replaceAll("\\s+", " ").trim();
        return compact.length() <= 280 ? compact : compact.substring(0, 280) + "…";
    }
}
