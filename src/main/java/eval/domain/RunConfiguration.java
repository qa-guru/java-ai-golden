package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunConfiguration(
        String mode,
        String model,
        String judgeModel,
        boolean judgeEnabled,
        int repetitions,
        boolean includeRed,
        String artifactMode,
        String outputDir
) {
    public Map<String, String> asMap() {
        return Map.of(
                "mode", String.valueOf(mode),
                "model", String.valueOf(model),
                "judgeModel", String.valueOf(judgeModel),
                "judgeEnabled", String.valueOf(judgeEnabled),
                "repetitions", String.valueOf(repetitions),
                "includeRed", String.valueOf(includeRed),
                "artifactMode", String.valueOf(artifactMode),
                "outputDir", String.valueOf(outputDir));
    }

    public List<String> differences(RunConfiguration other) {
        if (other == null) {
            return List.of("other configuration is missing");
        }
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        asMap().forEach((k, v) -> {
            String ov = other.asMap().get(k);
            if (!java.util.Objects.equals(v, ov) && !"outputDir".equals(k)) {
                out.add(k + ": " + v + " vs " + ov);
            }
        });
        return List.copyOf(out);
    }
}
