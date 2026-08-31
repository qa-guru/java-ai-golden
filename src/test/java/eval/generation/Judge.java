package eval.generation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class Judge {

    enum Verdict {
        ACCEPTED,
        REJECTED,
        PENDING
    }

    private Judge() {
    }

    static String review(GoldenCase row, String candidate) throws IOException, InterruptedException {
        String model = System.getProperty(
                "judgeModel",
                System.getProperty("model", "qwen2.5-coder:7b"));
        return OllamaClient.chat(system(), user(row, candidate), model);
    }

    static Verdict parse(String judgeOut) {
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

    static void assertAccepted(GoldenCase row, String judgeOut) {
        assertEquals(
                Verdict.ACCEPTED,
                parse(judgeOut),
                () -> row.id() + " judge:\n" + judgeOut);
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

    private static String user(GoldenCase row, String candidate) {
        return """
                MODE=%s
                golden.id=%s
                golden.prompt=%s
                expect.layer=%s
                expect.class=%s
                expect.contains=%s
                expect.rag=%s
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
                row.expect().rag(),
                row.mustNot(),
                candidate);
    }
}
