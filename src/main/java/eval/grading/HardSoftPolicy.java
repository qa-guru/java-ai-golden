package eval.grading;

import eval.domain.ContractResult;
import eval.domain.EvalStatus;
import eval.domain.JudgeDecision;
import eval.domain.JudgeResult;

/**
 * Hard (contract) constraints outrank the LLM judge. Soft quality cannot compensate a hard fail.
 * Judge ACCEPT on a contract FAIL is recorded but the attempt is still FAIL.
 */
public final class HardSoftPolicy {

    private HardSoftPolicy() {
    }

    /**
     * Overall attempt status from hard + optional soft judge.
     * Judge REJECT does not flip a contract FAIL into PASS (impossible) and does not
     * flip a contract PASS into FAIL: mill live treats REJECT as informational.
     * Overall pass rate is therefore hard-gated. {@code judgeAcceptRate} is the soft metric.
     */
    public static EvalStatus hardStatus(ContractResult contract) {
        if (contract == null) {
            return EvalStatus.FAIL;
        }
        return contract.passed() ? EvalStatus.PASS : EvalStatus.FAIL;
    }

    /** True when a judge ACCEPT would illegally override a hard fail. */
    public static boolean judgeOverrideAttempted(ContractResult contract, JudgeResult judge) {
        if (contract == null || contract.passed() || judge == null) {
            return false;
        }
        return judge.decision() == JudgeDecision.ACCEPT;
    }
}
