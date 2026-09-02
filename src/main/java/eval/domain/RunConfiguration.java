package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunConfiguration(
        String mode,
        String model,
        String judgeModel,
        boolean judgeEnabled,
        int repetitions,
        boolean includeRed,
        String artifactMode,
        String outputDir,
        String provider,
        String experimentId,
        String datasetSplit,
        String javaVersion
) {
    public RunConfiguration(
            String mode,
            String model,
            String judgeModel,
            boolean judgeEnabled,
            int repetitions,
            boolean includeRed,
            String artifactMode,
            String outputDir,
            String provider) {
        this(
                mode,
                model,
                judgeModel,
                judgeEnabled,
                repetitions,
                includeRed,
                artifactMode,
                outputDir,
                provider,
                null,
                "development",
                null);
    }

    public RunConfiguration(
            String mode,
            String model,
            String judgeModel,
            boolean judgeEnabled,
            int repetitions,
            boolean includeRed,
            String artifactMode,
            String outputDir,
            String provider,
            String experimentId,
            String datasetSplit) {
        this(
                mode,
                model,
                judgeModel,
                judgeEnabled,
                repetitions,
                includeRed,
                artifactMode,
                outputDir,
                provider,
                experimentId,
                datasetSplit,
                null);
    }

    public static String currentJavaVersion() {
        String v = System.getProperty("java.specification.version");
        return v == null || v.isBlank() ? "unknown" : v;
    }

    public Map<String, String> asMap() {
        return Map.ofEntries(
                Map.entry("mode", String.valueOf(mode)),
                Map.entry("model", String.valueOf(model)),
                Map.entry("judgeModel", String.valueOf(judgeModel)),
                Map.entry("judgeEnabled", String.valueOf(judgeEnabled)),
                Map.entry("repetitions", String.valueOf(repetitions)),
                Map.entry("includeRed", String.valueOf(includeRed)),
                Map.entry("artifactMode", String.valueOf(artifactMode)),
                Map.entry("outputDir", String.valueOf(outputDir)),
                Map.entry("provider", String.valueOf(provider)),
                Map.entry("experimentId", String.valueOf(experimentId)),
                Map.entry("datasetSplit", String.valueOf(datasetSplit)),
                Map.entry("javaVersion", String.valueOf(javaVersion)));
    }

    public List<String> differences(RunConfiguration other) {
        if (other == null) {
            return List.of("other configuration is missing");
        }
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        asMap().forEach((k, v) -> {
            String ov = other.asMap().get(k);
            if (!Objects.equals(v, ov)
                    && !"outputDir".equals(k)
                    && !"artifactMode".equals(k)
                    && !"javaVersion".equals(k)) {
                out.add(k + ": " + v + " vs " + ov);
            }
        });
        return List.copyOf(out);
    }
}
