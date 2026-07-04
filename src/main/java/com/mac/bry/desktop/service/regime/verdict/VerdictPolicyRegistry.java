package com.mac.bry.desktop.service.regime.verdict;

import com.mac.bry.desktop.model.regime.RunMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Rejestr polityk werdyktu — wybiera implementację {@link VerdictPolicy}
 * na podstawie zadeklarowanego {@link RunMode} (DP-001 §4.5, wzorzec Strategy).
 * Polityki wstrzykiwane przez Springa — dodanie nowego trybu wymaga tylko
 * nowego komponentu implementującego interfejs.
 */
@Component
public class VerdictPolicyRegistry {

    private final Map<RunMode, VerdictPolicy> policies = new EnumMap<>(RunMode.class);

    public VerdictPolicyRegistry(List<VerdictPolicy> allPolicies) {
        for (VerdictPolicy policy : allPolicies) {
            policies.put(policy.appliesTo(), policy);
        }
    }

    /**
     * Zwraca politykę dla trybu; fallback do CHARACTERIZATION (bezpieczna —
     * nie nakłada kryteriów kwalifikacyjnych) gdy tryb nieznany lub null.
     */
    public VerdictPolicy forMode(RunMode runMode) {
        VerdictPolicy policy = runMode != null ? policies.get(runMode) : null;
        if (policy == null) {
            policy = policies.get(RunMode.CHARACTERIZATION);
        }
        if (policy == null) {
            throw new IllegalStateException(
                    "Brak zarejestrowanej polityki werdyktu (nawet fallback CHARACTERIZATION)");
        }
        return policy;
    }
}
