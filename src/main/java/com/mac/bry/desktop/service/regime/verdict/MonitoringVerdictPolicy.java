package com.mac.bry.desktop.service.regime.verdict;

import com.mac.bry.desktop.model.regime.RunMode;
import com.mac.bry.desktop.model.regime.VerdictStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Polityka nadzoru operacyjnego (DP-001 §4.5). Docelowo: porównanie z baseline
 * poprzedniej kwalifikacji. Do czasu wdrożenia baseline (przyszła faza) odchylenia
 * od kryteriów raportowane są jako WARNING z zaleceniem porównania ręcznego —
 * monitoring nie wydaje werdyktu kwalifikacji absolutnej.
 */
@Component
public class MonitoringVerdictPolicy implements VerdictPolicy {

    @Override
    public RunMode appliesTo() {
        return RunMode.MONITORING;
    }

    @Override
    public VerdictResult evaluate(VerdictContext ctx) {
        if (!ctx.isHasSteadyStateData()) {
            return VerdictResult.of(VerdictStatus.INCONCLUSIVE,
                    "brak danych STEADY_STATE — monitoring wymaga fazy ustalonej");
        }

        List<String> warnings = new ArrayList<>();

        long excursions = ctx.countExcursionsInSteadyEnvelope();
        if (excursions > 0) {
            warnings.add(String.format(
                    "%d ekskursja(-e) w fazie ustalonej — porównaj z baseline "
                            + "poprzedniej kwalifikacji", excursions));
        }
        if (Boolean.FALSE.equals(ctx.getCpkPass()) || Boolean.FALSE.equals(ctx.getStdDevPass())) {
            warnings.add("odchylenie metryk od kryteriów kwalifikacyjnych — porównaj "
                    + "z baseline poprzedniej kwalifikacji");
        }

        if (!warnings.isEmpty()) {
            return new VerdictResult(VerdictStatus.WARNING, warnings);
        }
        return VerdictResult.pass();
    }
}
