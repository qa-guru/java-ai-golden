package eval.reporting;

import eval.domain.EvalRun;
import eval.domain.QualityGateResult;
import eval.domain.RunConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a static GitHub Pages tree from {@code build/eval}: one folder per protocol slot
 * (latest run, preferring a directory that already has {@code comparison.json}).
 */
public final class PagesSite {

    static final List<String> SLOT_ORDER = List.of(
            "development", "holdout", "live", "nightly", "calibration");

    private PagesSite() {
    }

    public static Path write(Path evalDir, Path siteDir) {
        try {
            if (!Files.isDirectory(evalDir)) {
                throw new IllegalArgumentException("eval dir does not exist: " + evalDir);
            }
            Files.createDirectories(siteDir);
            Files.writeString(siteDir.resolve(".nojekyll"), "", StandardCharsets.UTF_8);

            Map<String, Candidate> best = new LinkedHashMap<>();
            try (var stream = Files.list(evalDir)) {
                for (Path dir : stream.filter(Files::isDirectory).toList()) {
                    Path runJson = dir.resolve("run.json");
                    Path report = dir.resolve("eval-report.md");
                    if (!Files.isRegularFile(runJson) || !Files.isRegularFile(report)) {
                        continue;
                    }
                    EvalRun run = ReportIo.readRun(runJson);
                    Candidate next = new Candidate(
                            slotOf(run),
                            run,
                            dir,
                            Files.isRegularFile(dir.resolve("comparison.json")));
                    Candidate prev = best.get(next.slot());
                    if (better(next, prev)) {
                        best.put(next.slot(), next);
                    }
                }
            }

            List<Published> published = new ArrayList<>();
            for (String slot : SLOT_ORDER) {
                Candidate candidate = best.get(slot);
                if (candidate != null) {
                    published.add(writeRun(siteDir, candidate));
                }
            }
            Path calibrationMd = evalDir.resolve("calibration").resolve("report.md");
            if (Files.isRegularFile(calibrationMd)) {
                published.add(writeMarkdownSlot(
                        siteDir,
                        "calibration",
                        "Judge calibration",
                        Files.readString(calibrationMd, StandardCharsets.UTF_8),
                        "canned or live judge corpus; does not gate production scores"));
            }
            if (published.isEmpty()) {
                throw new IllegalArgumentException("no eval-report.md under " + evalDir);
            }
            Files.writeString(
                    siteDir.resolve("index.html"),
                    renderIndex(published),
                    StandardCharsets.UTF_8);
            return siteDir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String slotOf(EvalRun run) {
        RunConfiguration c = run.configuration();
        if (c == null) {
            return "development";
        }
        if ("LIVE".equalsIgnoreCase(c.mode())) {
            if (c.includeRed() && c.repetitions() >= 5) {
                return "nightly";
            }
            return "live";
        }
        String split = c.datasetSplit();
        if (split != null && !split.isBlank() && !"development".equalsIgnoreCase(split)) {
            return split.toLowerCase(Locale.ROOT);
        }
        return "development";
    }

    static String titleOf(String slot) {
        return switch (slot) {
            case "development" -> "Development (fixtures)";
            case "holdout" -> "Holdout";
            case "live" -> "Live smoke";
            case "nightly" -> "Nightly";
            case "calibration" -> "Judge calibration";
            default -> slot;
        };
    }

    private static boolean better(Candidate next, Candidate prev) {
        if (prev == null) {
            return true;
        }
        if (next.compared() != prev.compared()) {
            return next.compared();
        }
        int time = Objects.toString(next.run().timestamp(), "")
                .compareTo(Objects.toString(prev.run().timestamp(), ""));
        if (time != 0) {
            return time > 0;
        }
        return next.run().runId().compareTo(prev.run().runId()) > 0;
    }

    private static Published writeRun(Path siteDir, Candidate candidate) throws IOException {
        String md = Files.readString(candidate.dir().resolve("eval-report.md"), StandardCharsets.UTF_8);
        Path dest = siteDir.resolve(candidate.slot());
        Files.createDirectories(dest);
        Files.writeString(dest.resolve("eval-report.md"), md, StandardCharsets.UTF_8);
        Files.copy(
                candidate.dir().resolve("run.json"),
                dest.resolve("run.json"),
                StandardCopyOption.REPLACE_EXISTING);
        Path summary = candidate.dir().resolve("summary.json");
        if (Files.isRegularFile(summary)) {
            Files.copy(summary, dest.resolve("summary.json"), StandardCopyOption.REPLACE_EXISTING);
        }
        Path comparison = candidate.dir().resolve("comparison.json");
        if (Files.isRegularFile(comparison)) {
            Files.copy(comparison, dest.resolve("comparison.json"), StandardCopyOption.REPLACE_EXISTING);
        }
        String title = titleOf(candidate.slot());
        Files.writeString(
                dest.resolve("index.html"),
                MarkdownHtml.document(title + " · java-ai-golden", md, "../index.html"),
                StandardCharsets.UTF_8);
        return new Published(candidate.slot(), title, candidate.slot() + "/index.html", summaryOf(candidate.run()));
    }

    private static Published writeMarkdownSlot(
            Path siteDir, String slot, String title, String markdown, String summary) throws IOException {
        Path dest = siteDir.resolve(slot);
        Files.createDirectories(dest);
        Files.writeString(dest.resolve("eval-report.md"), markdown, StandardCharsets.UTF_8);
        Files.writeString(
                dest.resolve("index.html"),
                MarkdownHtml.document(title + " · java-ai-golden", markdown, "../index.html"),
                StandardCharsets.UTF_8);
        return new Published(slot, title, slot + "/index.html", summary);
    }

    static String summaryOf(EvalRun run) {
        String pass = SummaryView.formatRate(run.metrics() == null ? null : run.metrics().overallPassRate());
        QualityGateResult gate = run.qualityGate();
        String verdict = gate == null ? "gate n/a" : gate.verdict();
        return pass + " · `" + run.runId() + "` · " + verdict;
    }

    static String renderIndex(List<Published> published) {
        List<Published> ordered = new ArrayList<>(published);
        ordered.sort(Comparator.comparingInt(p -> {
            int i = SLOT_ORDER.indexOf(p.slot());
            return i < 0 ? SLOT_ORDER.size() : i;
        }));
        StringBuilder items = new StringBuilder();
        for (Published p : ordered) {
            items.append("        <li><a href=\"")
                    .append(MarkdownHtml.escapeAttr(p.href()))
                    .append("\"><strong>")
                    .append(MarkdownHtml.escape(p.title()))
                    .append("</strong></a>")
                    .append("<div class=\"muted\">")
                    .append(MarkdownHtml.inline(p.summary()))
                    .append("</div></li>\n");
        }
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>java-ai-golden eval</title>
                  <style>{{css}}</style>
                </head>
                <body>
                  <main>
                    <h1>java-ai-golden eval</h1>
                    <p>Latest reports from this GitHub Actions run on <code>main</code>.
                    Fixture numbers are contract checks on recorded answers, not a live-model score.
                    Live smoke and nightly stay in Actions artifacts unless that job wrote into the same <code>build/eval</code> tree.</p>
                    <h2>Reports</h2>
                    <ul class="slots">
{{items}}                    </ul>
                    <p class="muted">Each folder also has <code>eval-report.md</code> and <code>run.json</code>.
                    Semantics: <a href="https://github.com/qa-guru/java-ai-golden/blob/main/docs/evaluation-methodology.md">evaluation-methodology.md</a>.</p>
                  </main>
                </body>
                </html>
                """
                .replace("{{css}}", css())
                .replace("{{items}}", items);
    }

    private static String css() {
        return """
                :root { color-scheme: light; }
                body { margin: 0; font-family: ui-sans-serif, system-ui, sans-serif; line-height: 1.5; color: #1f2328; background: #fff; }
                main { max-width: 52rem; margin: 0 auto; padding: 1.5rem 1.25rem 3rem; }
                a { color: #0969da; }
                .muted { color: #656d76; }
                .slots { list-style: none; padding: 0; }
                .slots li { border: 1px solid #d0d7de; border-radius: 8px; padding: 0.85rem 1rem; margin: 0.6rem 0; }
                code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.9em; background: #f6f8fa; padding: 0.1em 0.35em; border-radius: 4px; }
                """;
    }

    record Candidate(String slot, EvalRun run, Path dir, boolean compared) {
    }

    record Published(String slot, String title, String href, String summary) {
    }
}
