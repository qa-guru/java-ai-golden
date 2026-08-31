package eval.generation;

import java.util.List;

final class RagCite {

    private RagCite() {
    }

    static String withRetrieverHeader(String out, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        String header = "RAG: " + String.join(", ", ids);
        String rest = out == null ? "" : out.strip();
        if (rest.regionMatches(true, 0, "RAG:", 0, 4)) {
            int nl = rest.indexOf('\n');
            rest = nl < 0 ? "" : rest.substring(nl + 1).stripLeading();
        }
        if (rest.isEmpty()) {
            return header;
        }
        return header + "\n\n" + rest;
    }
}
