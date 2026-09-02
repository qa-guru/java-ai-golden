package eval.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eval.domain.JudgeDecision;
import eval.domain.JudgeResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Judge {

    public enum Verdict {
        ACCEPTED,
        REJECTED,
        PENDING
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Judge() {
    }

    public static String review(GoldenCase row, String candidate) throws IOException, InterruptedException {
        return review(row, candidate, List.of());
    }

    public static String review(GoldenCase row, String candidate, List<String> retrieved)
            throws IOException, InterruptedException {
        String model = System.getProperty(
                "judgeModel",
                System.getProperty("model", "qwen2.5-coder:7b"));
        return review(row, candidate, retrieved, new OllamaClient(), model);
    }

    public static String review(
            GoldenCase row,
            String candidate,
            List<String> retrieved,
            eval.provider.ModelRunner runner,
            String model)
            throws eval.provider.EvalInfrastructureException, InterruptedException {
        return runner.complete(system(), user(row, candidate, retrieved), model).content();
    }

    static Verdict parse(String judgeOut) {
        return parseVerdictLine(judgeOut);
    }

    public static JudgeResult parseResult(String judgeOut) {
        if (judgeOut == null || judgeOut.isBlank()) {
            return JudgeResult.pending(judgeOut);
        }
        JudgeResult json = tryParseJson(judgeOut);
        if (json != null && json.schemaValid()) {
            return json;
        }
        Verdict v = parseVerdictLine(judgeOut);
        List<String> reasons = json == null ? List.of() : json.reasons();
        return new JudgeResult(toDecision(v), null, reasons, json == null, judgeOut);
    }

    static Verdict parseVerdictLine(String judgeOut) {
        if (judgeOut == null) {
            return Verdict.PENDING;
        }
        for (String line : judgeOut.split("\\R")) {
            String upper = line.strip().toUpperCase(Locale.ROOT);
            if (!upper.contains("VERDICT:")) {
                continue;
            }
            if (upper.contains("НЕ ПРИНЯТО") || upper.contains("REJECTED") || upper.contains("NOT_APPROVED")) {
                return Verdict.REJECTED;
            }
            if (upper.contains("ОЖИДАЕТ") || upper.contains("PENDING")) {
                return Verdict.PENDING;
            }
            if (upper.contains("ПРИНЯТО") || upper.contains("ACCEPTED") || upper.contains("APPROVED")) {
                return Verdict.ACCEPTED;
            }
        }
        return Verdict.PENDING;
    }

    static JudgeResult tryParseJson(String judgeOut) {
        String json = extractJsonObject(judgeOut);
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isObject()) {
                return JudgeResult.invalidSchema(judgeOut, "judge JSON is not an object");
            }
            if (!root.has("decision")) {
                return JudgeResult.invalidSchema(judgeOut, "judge JSON missing decision");
            }
            JudgeDecision decision = parseDecisionToken(root.get("decision").asText());
            if (decision == null) {
                return JudgeResult.invalidSchema(judgeOut, "judge JSON decision not ACCEPT|REJECT|PENDING");
            }
            Double score = null;
            if (root.has("score") && !root.get("score").isNull()) {
                if (!root.get("score").isNumber()) {
                    return JudgeResult.invalidSchema(judgeOut, "judge JSON score is not a number");
                }
                score = root.get("score").asDouble();
                if (score < 0 || score > 1) {
                    return JudgeResult.invalidSchema(judgeOut, "judge JSON score out of range [0,1]");
                }
            }
            List<String> reasons = new ArrayList<>();
            if (root.has("reasons")) {
                JsonNode rs = root.get("reasons");
                if (!rs.isArray()) {
                    return JudgeResult.invalidSchema(judgeOut, "judge JSON reasons is not an array");
                }
                for (JsonNode r : rs) {
                    reasons.add(r.asText());
                }
            }
            return new JudgeResult(decision, score, reasons, true, judgeOut);
        } catch (IOException e) {
            return JudgeResult.invalidSchema(judgeOut, "judge JSON malformed: " + e.getMessage());
        }
    }

    static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    static JudgeDecision parseDecisionToken(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (t) {
            case "ACCEPT", "ACCEPTED", "APPROVED", "ПРИНЯТО" -> JudgeDecision.ACCEPT;
            case "REJECT", "REJECTED", "NOT_APPROVED", "НЕ_ПРИНЯТО" -> JudgeDecision.REJECT;
            case "PENDING", "ОЖИДАЕТ" -> JudgeDecision.PENDING;
            default -> null;
        };
    }

    static JudgeDecision toDecision(Verdict v) {
        return switch (v) {
            case ACCEPTED -> JudgeDecision.ACCEPT;
            case REJECTED -> JudgeDecision.REJECT;
            case PENDING -> JudgeDecision.PENDING;
        };
    }

    static Verdict toVerdict(JudgeDecision d) {
        return switch (d) {
            case ACCEPT -> Verdict.ACCEPTED;
            case REJECT -> Verdict.REJECTED;
            case PENDING -> Verdict.PENDING;
        };
    }

    private static String system() {
        try {
            String rubric = Files.readString(
                    GoldenReader.evalDir().resolve("rubric-judge.md"),
                    StandardCharsets.UTF_8);
            return rubric
                    + """

                    Ты LLM-as-a-judge, не автор ответа. Не пиши новый тест.
                    Первая строка ответа СТРОГО одна из:
                    VERDICT: ПРИНЯТО
                    VERDICT: НЕ ПРИНЯТО
                    VERDICT: ОЖИДАЕТ
                    Дальше — таблица критерий | pass/fail | улика (цитата из кандидата).
                    must_not: pass = запрещённой строки НЕТ. fail = строка ЕСТЬ.
                    Не ставь fail за отсутствие submitExpectingError / git commit, если это must_not.
                    Чужой MODE не применяй: в form-happy не требуй submitExpectingError.
                    Опционально после таблицы — JSON-объект:
                    {"decision":"ACCEPT|REJECT|PENDING","score":0.0-1.0,"reasons":["..."]}
                    """;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String mode(GoldenCase row) {
        List<String> contains = row.expect().contains();
        if (contains.contains("submitExpectingError")) {
            return "form-negative";
        }
        if (contains.contains("fillAndSubmitForm")) {
            return "form-happy";
        }
        if ("api".equals(row.expect().layer())) {
            return "api-negative";
        }
        return "other";
    }

    private static String user(GoldenCase row, String candidate, List<String> retrieved) {
        return """
                MODE=%s
                golden.id=%s
                golden.prompt=%s
                expect.layer=%s
                expect.class=%s
                expect.contains=%s
                retrieved=%s
                must_not=%s

                CANDIDATE:
                %s
                """.formatted(
                mode(row),
                row.id(),
                row.prompt(),
                row.expect().layer(),
                row.expect().className(),
                row.expect().contains(),
                retrieved == null ? List.of() : retrieved,
                row.mustNot(),
                candidate);
    }
}
