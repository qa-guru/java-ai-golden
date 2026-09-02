package eval.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DatasetManifest(
        String version,
        int schema,
        String name,
        String description,
        List<String> changelog
) {
    public DatasetManifest {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("dataset version is required");
        }
        changelog = changelog == null ? List.of() : List.copyOf(changelog);
        name = name == null ? "" : name;
        description = description == null ? "" : description;
    }
}
