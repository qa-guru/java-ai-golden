package eval.generation;

import eval.pack.LexicalRetriever;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class WorkflowPrompt {

    private WorkflowPrompt() {
    }

    public record Built(String system, List<String> retrieved) {
    }

    public static String system(GoldenCase row) {
        return build(row).system();
    }

    public static Built build(GoldenCase row) {
        var parts = new ArrayList<String>();
        parts.add("Ты QA-агент в AI-first workflow. Соблюдай rules, skill, RAG и ADR.");
        parts.add(load("/pack/rules.md"));
        parts.add(load("/pack/qa-write-test.md"));
        if (row.expect().refused()) {
            return new Built(String.join("\n\n", parts), List.of());
        }
        parts.add(load("/pack/context/login-po.md"));
        parts.add(load("/pack/adr/009-login-401-is-api.md"));
        List<String> ids = LexicalRetriever.retrieve(row.prompt());
        if (ids.isEmpty()) {
            throw new IllegalStateException(row.id() + ": retriever returned no chunks");
        }
        for (String id : ids) {
            parts.add("### RAG id=" + id + "\n" + load("/pack/rag/" + id + ".md"));
        }
        return new Built(String.join("\n\n", parts), List.copyOf(ids));
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
