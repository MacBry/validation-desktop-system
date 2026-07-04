package com.mac.bry.desktop.service.regime.verdict;

import com.mac.bry.desktop.model.regime.RunMode;
import com.mac.bry.desktop.model.regime.VerdictStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Polityka kwalifikacyjna (IQ/OQ/PQ) — rygorystyczne kryteria WHO/GMP (DP-001 §4.5).
 * <ul>
 *   <li>Przebieg dynamiczny (mało STEADY_STATE) → INCONCLUSIVE — kryteria
 *       kwalifikacyjne mogą nie mieć zastosowania.</li>
 *   <li>Niezidentyfikowana ekskursja w oknie ustalonym → FAIL.</li>
 *   <li>Cpk(STEADY) &lt; 1,0 → FAIL.</li>
 *   <li>std dev(STEADY) &gt; limit WHO → WARNING.</li>
 * </ul>
 */
@Component
public class QualificationVerdictPolicy implements VerdictPolicy {

    /** Minimalne pokrycie STEADY_STATE, poniżej którego kwalifikacja jest nierozstrzygalna [%]. */
    static final double MIN_STEADY_COVERAGE_PERCENT = 20.0;

    @Override
    public RunMode appliesTo() {
        return RunMode.QUALIFICATION;
    }

    @Override
    public VerdictResult evaluate(VerdictContext ctx) {
        if (!ctx.isHasSteadyStateData()
                || ctx.getSteadyStateCoveragePercent() < MIN_STEADY_COVERAGE_PERCENT) {
            return VerdictResult.of(VerdictStatus.INCONCLUSIVE, String.format(
                    "przebieg dynamiczny (STEADY_STATE %.0f%% przebiegu) — "
                            + "kryteria kwalifikacyjne mogą nie mieć zastosowania",
                    ctx.getSteadyStateCoveragePercent()));
        }

        List<String> failReasons = new ArrayList<>();

        long excursions = ctx.countExcursionsInSteadyEnvelope();
        if (excursions > 0) {
            failReasons.add(String.format(
                    "%d niezidentyfikowana(-e) ekskursja(-e) w oknie fazy ustalonej", excursions));
        }
        if (Boolean.FALSE.equals(ctx.getCpkPass())) {
            failReasons.add("Cpk(STEADY) < 1,0 — niezdolność procesu w fazie ustalonej");
        }
        if (!failReasons.isEmpty()) {
            return new VerdictResult(VerdictStatus.FAIL, failReasons);
        }

        if (Boolean.FALSE.equals(ctx.getStdDevPass())) {
            return VerdictResult.of(VerdictStatus.WARNING,
                    "std dev(STEADY) przekracza limit WHO");
        }

        return VerdictResult.pass();
    }
}
