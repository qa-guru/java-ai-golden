package eval.generation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import eval.domain.JudgeDecision;
import eval.domain.Rate;
import eval.pack.LexicalRetriever;
import eval.provider.EvalInfrastructureException;
import eval.provider.ModelRunner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline / opt-in live judge validation. Does not affect production eval scores or the quality gate.
 */
public final class JudgeCalibration {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JudgeCalibration() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LabeledCase(
            String id,
            String caseId,
            String humanExpectedDecision,
            String judgeOutput,
            String note,
            String candidate
    ) {
        public String candidateOrFixture() {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
            return GoldenReader.fixture(caseId);
        }
    }

    public record Confusion(
            int trueAccept,
            int falseAccept,
            int falseReject,
            int trueReject
    ) {
        public int n() {
            return trueAccept + falseAccept + falseReject + trueReject;
        }
    }

    public record Report(
            int n,
            Rate accuracy,
            Rate precision,
            Rate recall,
            Double f1,
            Confusion confusion,
            String caveat,
            boolean live
    ) {
        public Report(int n, Rate accuracy, Rate precision, Rate recall, Double f1, Confusion confusion, String caveat) {
            this(n, accuracy, precision, recall, f1, confusion, caveat, false);
        }
    }

    public static List<LabeledCase> load(Path jsonl) {
        try {
            List<LabeledCase> rows = new ArrayList<>();
            for (String line : Files.readAllLines(jsonl, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                rows.add(MAPPER.readValue(line, LabeledCase.class));
            }
            return List.copyOf(rows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path defaultFile() {
        return GoldenReader.evalDir().resolve("calibration").resolve("judge-calibration.jsonl");
    }

    public static Report evaluate(List<LabeledCase> rows) {
        List<JudgeDecision> predicted = new ArrayList<>();
        for (LabeledCase row : rows) {
            predicted.add(Judge.parseResult(row.judgeOutput()).decision());
        }
        return score(rows, predicted, false, "canned judgeOutput");
    }

    /**
     * Re-judge stored candidates (or fixtures) with a live model. Not wired into production eval.
     */
    public static Report evaluateLive(List<LabeledCase> rows, ModelRunner runner, String model)
            throws EvalInfrastructureException, InterruptedException {
        List<JudgeDecision> predicted = new ArrayList<>();
        for (LabeledCase row : rows) {
            GoldenCase golden = GoldenReader.require(row.caseId());
            List<String> retrieved = golden.expect().refused()
                    ? List.of()
                    : LexicalRetriever.retrieve(golden.prompt());
            String raw = Judge.review(golden, row.candidateOrFixture(), retrieved, runner, model);
            predicted.add(Judge.parseResult(raw).decision());
        }
        return score(rows, predicted, true, "live judge");
    }

    static Report score(List<LabeledCase> rows, List<JudgeDecision> predicted, boolean live, String source) {
        int ta = 0;
        int fa = 0;
        int fr = 0;
        int tr = 0;
        int skipped = 0;
        for (int i = 0; i < rows.size(); i++) {
            JudgeDecision human = Judge.parseDecisionToken(rows.get(i).humanExpectedDecision());
            JudgeDecision pred = predicted.get(i);
            if (human == null || human == JudgeDecision.PENDING || pred == JudgeDecision.PENDING) {
                skipped++;
                continue;
            }
            boolean humanAccept = human == JudgeDecision.ACCEPT;
            boolean predAccept = pred == JudgeDecision.ACCEPT;
            if (humanAccept && predAccept) {
                ta++;
            } else if (!humanAccept && predAccept) {
                fa++;
            } else if (humanAccept) {
                fr++;
            } else {
                tr++;
            }
        }
        int n = ta + fa + fr + tr;
        Rate accuracy = Rate.of(ta + tr, n);
        Rate precision = Rate.of(ta, ta + fa);
        Rate recall = Rate.of(ta, ta + fr);
        Double f1 = null;
        if (precision.defined() && recall.defined() && (precision.value() + recall.value()) > 0) {
            f1 = 2.0 * precision.value() * recall.value() / (precision.value() + recall.value());
        }
        String sizeNote = n < 30
                ? "n=" + n + " (skipped PENDING=" + skipped + ") is too small for a published judge-accuracy claim"
                : "n=" + n + " (skipped PENDING=" + skipped + ")";
        String caveat = sizeNote + "; source=" + source
                + "; does not affect production eval or the quality gate";
        return new Report(n, accuracy, precision, recall, f1, new Confusion(ta, fa, fr, tr), caveat, live);
    }

    public static String render(Report report) {
        StringBuilder md = new StringBuilder();
        md.append("# Judge calibration\n\n");
        md.append("- Live: ").append(report.live()).append('\n');
        md.append("- n: ").append(report.n()).append('\n');
        md.append("- Accuracy: ").append(report.accuracy().asPercentAndCount()).append('\n');
        md.append("- Precision (ACCEPT=positive): ").append(report.precision().asPercentAndCount()).append('\n');
        md.append("- Recall: ").append(report.recall().asPercentAndCount()).append('\n');
        md.append("- F1: ").append(report.f1() == null ? "n/a" : String.format(Locale.ROOT, "%.3f", report.f1()))
                .append('\n');
        md.append("- Confusion TA/FA/FR/TR: ")
                .append(report.confusion().trueAccept()).append('/')
                .append(report.confusion().falseAccept()).append('/')
                .append(report.confusion().falseReject()).append('/')
                .append(report.confusion().trueReject()).append('\n');
        md.append("- Caveat: ").append(report.caveat()).append('\n');
        return md.toString();
    }
}
