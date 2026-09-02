package eval.grading;

import eval.domain.RetrievalResult;
import eval.generation.GoldenCase;
import eval.pack.LexicalRetriever;
import eval.pack.RagChunk;

import java.util.List;

public final class RetrievalGrader {

    private RetrievalGrader() {
    }

    public static RetrievalResult grade(GoldenCase row) {
        if (row.expect().refused()) {
            return RetrievalResult.notApplicable();
        }
        return RetrievalResult.of(LexicalRetriever.retrieve(row.prompt()), row.expect().rag());
    }

    public static RetrievalResult grade(GoldenCase row, List<RagChunk> corpus) {
        if (row.expect().refused()) {
            return RetrievalResult.notApplicable();
        }
        return RetrievalResult.of(LexicalRetriever.retrieve(row.prompt(), corpus), row.expect().rag());
    }
}
