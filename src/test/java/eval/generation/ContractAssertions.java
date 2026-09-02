package eval.generation;

import eval.grading.ContractGrader;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContractAssertions {

    private ContractAssertions() {
    }

    static void assertMatches(GoldenCase row, String out) {
        var result = ContractGrader.grade(row, out);
        assertTrue(result.passed(), () -> String.join("; ", result.violations()));
    }

    static boolean hasStepOnTests(String out) {
        return ContractGrader.hasStepOnTests(out);
    }
}
