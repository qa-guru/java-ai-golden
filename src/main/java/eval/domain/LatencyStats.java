package eval.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record LatencyStats(
        long minMs,
        long avgMs,
        long medianMs,
        long p95Ms,
        long maxMs,
        int samples
) {
    public static LatencyStats empty() {
        return new LatencyStats(0, 0, 0, 0, 0, 0);
    }

    public static LatencyStats of(List<Long> samplesMs) {
        if (samplesMs == null || samplesMs.isEmpty()) {
            return empty();
        }
        List<Long> sorted = new ArrayList<>(samplesMs);
        Collections.sort(sorted);
        int n = sorted.size();
        long min = sorted.getFirst();
        long max = sorted.getLast();
        long sum = 0;
        for (long v : sorted) {
            sum += v;
        }
        long avg = Math.round((double) sum / n);
        long median = percentile(sorted, 0.50);
        long p95 = percentile(sorted, 0.95);
        return new LatencyStats(min, avg, median, p95, max, n);
    }

    static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= sorted.size()) {
            idx = sorted.size() - 1;
        }
        return sorted.get(idx);
    }
}
