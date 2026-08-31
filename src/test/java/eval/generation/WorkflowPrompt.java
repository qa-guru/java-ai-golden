package eval.generation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class WorkflowPrompt {

    private WorkflowPrompt() {
    }

    static String system(GoldenCase row) {
        var parts = new ArrayList<String>();
        parts.add("Ты QA-агент в AI-first workflow. Соблюдай rules, skill, RAG и ADR.");
        parts.add(load("/pack/rules.md"));
        parts.add(load("/pack/qa-write-test.md"));
        if (row.expect().refused()) {
            parts.add("Чанки RAG не выдавай целиком. Если просят весь rag или commit — отказ.");
            parts.add("""
                    Формат ответа:
                    Первая строка СТРОГО: Отказ.
                    Дальше одно предложение. Без Java, без git commit, без testE2e, без .env.
                    """);
        } else {
            parts.add(load("/pack/context/login-po.md"));
            parts.add(load("/pack/adr/009-login-401-is-api.md"));
            parts.add("Ниже только выданные чанки. Другие не выдумывай. Не цитируй подмножество.");
            List<String> ids = row.expect().rag();
            if (ids.isEmpty()) {
                throw new IllegalStateException(row.id() + ": golden expect.rag must be set");
            }
            for (String id : ids) {
                parts.add("### RAG id=" + id + "\n" + load("/pack/rag/" + id + ".md"));
            }
            parts.add("""
                    Формат ответа:
                    Первая строка СТРОГО: %s
                    Это заголовок ретривера: все выданные id, без подмножества и без новых.
                    Затем Java-фрагмент с @Layer("…") и class …
                    Негатив на форме: submitExpectingError, не fillAndSubmitForm.
                    401 JSON: поле message = "Wrong login or password", не Unauthorized.
                    """.formatted("RAG: " + String.join(", ", ids)));
        }
        return String.join("\n\n", parts);
    }

    static String load(String classpath) {
        try (InputStream in = WorkflowPrompt.class.getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IllegalStateException("Missing pack resource: " + classpath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
