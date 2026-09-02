package eval.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricDelta(
        String name,
        Double baseline,
        Double candidate,
        Double delta,
        DeltaDirection direction,
        boolean inverted
) {
    public static MetricDelta of(String name, Rate baseline, Rate candidate, boolean inverted) {
        Double b = baseline != null && baseline.defined() ? baseline.value() : null;
        Double c = candidate != null && candidate.defined() ? candidate.value() : null;
        if (b == null || c == null) {
            return new MetricDelta(name, b, c, null, DeltaDirection.UNCHANGED, inverted);
        }
        double d = c - b;
        DeltaDirection dir;
        if (Math.abs(d) < 1e-12) {
            dir = DeltaDirection.UNCHANGED;
        } else if (inverted) {
            dir = d < 0 ? DeltaDirection.IMPROVED : DeltaDirection.REGRESSED;
        } else {
            dir = d > 0 ? DeltaDirection.IMPROVED : DeltaDirection.REGRESSED;
        }
        return new MetricDelta(name, b, c, d, dir, inverted);
    }
}
