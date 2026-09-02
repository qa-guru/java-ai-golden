package eval.execution;

public enum ArtifactMode {
    NEVER,
    FAILURE,
    ALWAYS;

    public static ArtifactMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return FAILURE;
        }
        return ArtifactMode.valueOf(raw.strip().toUpperCase());
    }
}
