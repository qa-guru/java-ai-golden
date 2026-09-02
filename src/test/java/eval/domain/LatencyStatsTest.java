package eval.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("eval")
@Tag("framework")
@DisplayName("Latency and rate helpers")
class LatencyStatsTest {

    @Test
    void percentilesOnSortedSamples() {
        LatencyStats stats = LatencyStats.of(List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L));
        assertEquals(10, stats.minMs());
        assertEquals(100, stats.maxMs());
        assertEquals(55, stats.avgMs());
        assertEquals(50, stats.medianMs());
        assertEquals(100, stats.p95Ms());
        assertEquals(10, stats.samples());
    }

    @Test
    void rateRejectsHitsAboveTotal() {
        assertThrows(IllegalArgumentException.class, () -> new Rate(2, 1));
    }

    @Test
    void tokenUsageDoesNotInventCost() {
        TokenUsage usage = TokenUsage.of(11, 7);
        assertEquals(18, usage.totalTokens());
        org.junit.jupiter.api.Assertions.assertNull(usage.estimatedCost());
    }
}
