package eval.domain;

import eval.generation.GoldenCase;

import java.util.LinkedHashSet;
import java.util.Set;

public final class CaseFlags {
    private CaseFlags() {
    }

    public static Set<CaseKind> of(GoldenCase row) {
        LinkedHashSet<CaseKind> kinds = new LinkedHashSet<>();
        kinds.add(CaseKind.GENERATION);
        if (row.expect().refused()) {
            kinds.add(CaseKind.NEGATIVE);
            kinds.add(CaseKind.REFUSAL);
        } else {
            kinds.add(CaseKind.RAG);
            kinds.add(CaseKind.RETRIEVAL);
        }
        if (row.expect().isRed()) {
            kinds.add(CaseKind.NEGATIVE);
        }
        if (row.id().startsWith("hallucinate-")) {
            kinds.add(CaseKind.HALLUCINATION);
            kinds.add(CaseKind.NEGATIVE);
        }
        if (row.expect().layer() != null || "mixed-layer".equals(row.id())) {
            kinds.add(CaseKind.LAYER);
        }
        return Set.copyOf(kinds);
    }

    public static boolean has(CaseResult result, CaseKind kind) {
        return result.kinds() != null && result.kinds().contains(kind);
    }
}
