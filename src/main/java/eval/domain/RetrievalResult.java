package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RetrievalResult(
        boolean applicable,
        boolean passed,
        List<String> retrieved,
        List<String> expected,
        List<String> missing,
        List<String> unexpected,
        boolean forbiddenRetrieval
) {
    public RetrievalResult {
        retrieved = retrieved == null ? List.of() : List.copyOf(retrieved);
        expected = expected == null ? List.of() : List.copyOf(expected);
        missing = missing == null ? List.of() : List.copyOf(missing);
        unexpected = unexpected == null ? List.of() : List.copyOf(unexpected);
    }

    public static RetrievalResult notApplicable() {
        return new RetrievalResult(false, true, List.of(), List.of(), List.of(), List.of(), false);
    }

    public static RetrievalResult of(List<String> retrieved, List<String> expected) {
        List<String> got = retrieved == null ? List.of() : retrieved;
        List<String> want = expected == null ? List.of() : expected;
        List<String> missing = want.stream().filter(id -> !got.contains(id)).toList();
        List<String> unexpected = got.stream().filter(id -> !want.contains(id)).toList();
        boolean passed = !got.isEmpty() && missing.isEmpty() && unexpected.isEmpty();
        return new RetrievalResult(true, passed, got, want, missing, unexpected, !unexpected.isEmpty());
    }
}
