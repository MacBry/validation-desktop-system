package com.mac.bry.desktop.service.regime.verdict;

import com.mac.bry.desktop.model.regime.RunMode;
import com.mac.bry.desktop.model.regime.VerdictStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Polityka charakteryzacyjna — obserwacja zachowania w realnym użyciu (DP-001 §4.5).
 * Te same obserwacje, które w kwalifikacji dają FAIL, tu są raportowane jako
 * FINDING z rekomendacją — dynamika jest sygnałem, nie naruszeniem.
 */
@Component
public class CharacterizationVerdictPolicy implements VerdictPolicy {

    @Override
    public RunMode appliesTo() {
        return RunMode.CHARACTERIZATION;
    }

    @Override
    public VerdictResult evaluate(VerdictContext ctx) {
        if (!ctx.isHasSteadyStateData()
                || ctx.getSteadyStateCoveragePercent() < QualificationVerdictPolicy.MIN_STEADY_COVERAGE_PERCENT) {
            return VerdictResult.of(VerdictStatus.INCONCLUSIVE, String.format(
                    "faza STEADY_STATE stanowi tylko %.0f%% przebiegu — metryki "
                            + "kwalifikacyjne prezentowane informacyjnie",
                    ctx.getSteadyStateCoveragePercent()));
        }

        List<String> findings = new ArrayList<>();

        long excursions = ctx.countExcursionsInSteadyEnvelope();
        if (excursions > 0) {
            findings.add(String.format(
                    "%d ekskursja(-e) w fazie ustalonej — rekomendacja: zweryfikować "
                            + "z logiem zdarzeń przed kwalifikacją", excursions));
        }
        if (Boolean.FALSE.equals(ctx.getCpkPass())) {
            findings.add("Cpk(STEADY) < 1,0 (przebieg charakteryzacyjny)");
        }
        if (Boolean.FALSE.equals(ctx.getStdDevPass())) {
            findings.add("std dev(STEADY) powyżej limitu WHO (przebieg charakteryzacyjny)");
        }

        if (!findings.isEmpty()) {
            return new VerdictResult(VerdictStatus.FINDING, findings);
        }
        return VerdictResult.pass();
    }
}
