package eval.generation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoldenCase(
        String id,
        String prompt,
        Expect expect,
        @JsonProperty("must_not") List<String> mustNot
) {
    public GoldenCase {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("golden row needs id");
        }
        if (expect == null) {
            expect = new Expect(null, null, null, null, null);
        }
        mustNot = mustNot == null ? List.of() : List.copyOf(mustNot);
    }

    @Override
    public String toString() {
        return id;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Expect(
            String layer,
            @JsonProperty("class") String className,
            Integer status,
            List<String> rag,
            Boolean refuse
    ) {
        public Expect {
            rag = rag == null ? List.of() : List.copyOf(rag);
        }

        public boolean refused() {
            return Boolean.TRUE.equals(refuse);
        }
    }
}
