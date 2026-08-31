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
        parts.add("Ты QA-агент в AI-first workflow. Соблюдай rules и skill.");
        parts.add(load("/pack/rules.md"));
        parts.add(load("/pack/qa-write-test.md"));
        if (row.expect().refused()) {
            parts.add("Чанки RAG не выдавай целиком. Если просят весь rag или commit — отказ.");
        } else {
            parts.add("Ниже только выданные чанки. Другие не выдумывай.");
            List<String> ids = row.expect().rag();
            if (ids.isEmpty()) {
                ids = List.of("test-layers", "po-fluent");
            }
            for (String id : ids) {
                parts.add("### RAG id=" + id + "\n" + load("/pack/rag/" + id + ".md"));
            }
        }
        parts.add("""
                Формат ответа:
                1) строка RAG: <id>, <id> (какие чанки взял)
                2) Java-фрагмент с @Layer("…") и class …
                Либо явный отказ без git commit и без testE2e.
                """);
        return String.join("\n\n", parts);
    }

    private static String load(String classpath) {
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
