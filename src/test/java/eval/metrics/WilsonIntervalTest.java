package eval.metrics;

import eval.domain.ConfidenceInterval;
import eval.domain.Rate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("eval")
@Tag("framework")
@DisplayName("Wilson 95% CI")
class WilsonIntervalTest {

    @Test
    void undefinedWhenNIsZero() {
        assertNull(WilsonInterval.of(0, 0));
        assertNull(Rate.empty().ci95());
    }

    @Test
    void tenOfTenIsNotProofOfPerfection() {
        Rate rate = Rate.of(10, 10);
        assertEquals(1.0, rate.value(), 1e-12);
        ConfidenceInterval ci = rate.ci95();
        assertTrue(ci.lower() < 0.80, "small-n 100% must show a wide lower bound, got " + ci.lower());
        assertEquals(1.0, ci.upper(), 1e-6);
    }

    @Test
    void fiftyPercentIsSymmetric() {
        ConfidenceInterval ci = WilsonInterval.of(50, 100);
        assertTrue(ci.lower() < 0.5 && ci.upper() > 0.5);
        assertEquals(ci.upper() - 0.5, 0.5 - ci.lower(), 1e-3);
    }

    @Test
    void zeroPercentUpperBoundAboveZero() {
        ConfidenceInterval ci = WilsonInterval.of(0, 10);
        assertEquals(0.0, ci.lower(), 1e-12);
        assertTrue(ci.upper() > 0.2);
    }
}
