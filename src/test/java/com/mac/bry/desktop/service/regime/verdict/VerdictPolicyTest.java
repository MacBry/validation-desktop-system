package com.mac.bry.desktop.service.regime.verdict;

import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.RunMode;
import com.mac.bry.desktop.model.regime.SegmentType;
import com.mac.bry.desktop.model.regime.VerdictStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Testy polityk werdyktu zależnych od trybu runu (DP-001 §4.5).
 * Kluczowa właściwość: ta sama obserwacja → różny werdykt per RunMode.
 */
class VerdictPolicyTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 6, 21, 8, 0);

    private final QualificationVerdictPolicy qualification = new QualificationVerdictPolicy();
    private final CharacterizationVerdictPolicy characterization = new CharacterizationVerdictPolicy();
    private final MonitoringVerdictPolicy monitoring = new MonitoringVerdictPolicy();

    // ── TC-VP-001: kluczowy kontrakt DP-001 §4.5 ─────────────────────────────

    @Test
    @DisplayName("TC-VP-001: Ekskursja w fazie ustalonej → QUALIFICATION=FAIL, CHARACTERIZATION=FINDING")
    void tc_vp_001_sameExcursion_differentVerdictPerMode() {
        VerdictContext ctx = contextWithExcursionInSteady();

        VerdictResult qual = qualification.evaluate(ctx);
        VerdictResult character = characterization.evaluate(ctx);

        assertThat(qual.status()).isEqualTo(VerdictStatus.FAIL);
        assertThat(character.status()).isEqualTo(VerdictStatus.FINDING);
        assertThat(character.formattedNote()).containsIgnoringCase("rekomendacja");
    }

    @Test
    @DisplayName("TC-VP-002: Przebieg dynamiczny + QUALIFICATION → INCONCLUSIVE z sygnałem o kryteriach")
    void tc_vp_002_dynamicRun_qualificationInconclusive() {
        VerdictContext ctx = VerdictContext.builder()
                .hasSteadyStateData(true)
                .steadyStateCoveragePercent(12.0)
                .segments(List.of())
                .build();

        VerdictResult result = qualification.evaluate(ctx);

        assertThat(result.status()).isEqualTo(VerdictStatus.INCONCLUSIVE);
        assertThat(result.formattedNote())
                .containsIgnoringCase("przebieg dynamiczny")
                .containsIgnoringCase("kryteria kwalifikacyjne");
    }

    @Test
    @DisplayName("TC-VP-003: Cpk fail → QUALIFICATION=FAIL, CHARACTERIZATION=FINDING")
    void tc_vp_003_cpkFail_perMode() {
        VerdictContext ctx = VerdictContext.builder()
                .hasSteadyStateData(true)
                .steadyStateCoveragePercent(80.0)
                .cpkPass(false)
                .segments(List.of())
                .build();

        assertThat(qualification.evaluate(ctx).status()).isEqualTo(VerdictStatus.FAIL);
        assertThat(characterization.evaluate(ctx).status()).isEqualTo(VerdictStatus.FINDING);
    }

    @Test
    @DisplayName("TC-VP-004: StdDev fail (Cpk OK) → QUALIFICATION=WARNING")
    void tc_vp_004_stdDevFail_qualificationWarning() {
        VerdictContext ctx = VerdictContext.builder()
                .hasSteadyStateData(true)
                .steadyStateCoveragePercent(80.0)
                .cpkPass(true)
                .stdDevPass(false)
                .segments(List.of())
                .build();

        assertThat(qualification.evaluate(ctx).status()).isEqualTo(VerdictStatus.WARNING);
    }

    @Test
    @DisplayName("TC-VP-005: Wszystko OK → PASS bez notatki (każdy tryb)")
    void tc_vp_005_allPass_noNote() {
        VerdictContext ctx = VerdictContext.builder()
                .hasSteadyStateData(true)
                .steadyStateCoveragePercent(90.0)
                .cpkPass(true)
                .stdDevPass(true)
                .segments(steadyOnlySegments())
                .build();

        for (VerdictPolicy policy : List.of(qualification, characterization, monitoring)) {
            VerdictResult result = policy.evaluate(ctx);
            assertThat(result.status()).as("polityka %s", policy.appliesTo()).isEqualTo(VerdictStatus.PASS);
            assertThat(result.formattedNote()).isNull();
        }
    }

    @Test
    @DisplayName("TC-VP-006: MONITORING — odchylenie metryk → WARNING z odesłaniem do baseline")
    void tc_vp_006_monitoring_deviationWarning() {
        VerdictContext ctx = VerdictContext.builder()
                .hasSteadyStateData(true)
                .steadyStateCoveragePercent(80.0)
                .cpkPass(false)
                .segments(List.of())
                .build();

        VerdictResult result = monitoring.evaluate(ctx);

        assertThat(result.status()).isEqualTo(VerdictStatus.WARNING);
        assertThat(result.formattedNote()).containsIgnoringCase("baseline");
    }

    @Test
    @DisplayName("TC-VP-007: DEFROST/DOOR_EVENT w fazie ustalonej nie są naruszeniem kwalifikacji")
    void tc_vp_007_explainedEventsDoNotFailQualification() {
        List<MeasurementSegment> segments = List.of(
                steady(T0, T0.plusHours(10)),
                segment(SegmentType.DEFROST, T0.plusHours(2), T0.plusHours(2).plusMinutes(30)),
                segment(SegmentType.DOOR_EVENT, T0.plusHours(5), T0.plusHours(5).plusMinutes(10)));

        VerdictContext ctx = VerdictContext.builder()
                .hasSteadyStateData(true)
                .steadyStateCoveragePercent(85.0)
                .cpkPass(true)
                .stdDevPass(true)
                .segments(segments)
                .build();

        assertThat(qualification.evaluate(ctx).status()).isEqualTo(VerdictStatus.PASS);
    }

    @Test
    @DisplayName("TC-VP-008: EXCURSION poza kopertą fazy ustalonej nie daje FAIL")
    void tc_vp_008_excursionOutsideSteadyEnvelope_noFail() {
        List<MeasurementSegment> segments = List.of(
                steady(T0.plusHours(4), T0.plusHours(10)),
                // Ekskursja w fazie dochodzenia do nastawy — przed kopertą STEADY
                segment(SegmentType.EXCURSION, T0, T0.plusMinutes(30)));

        VerdictContext ctx = VerdictContext.builder()
                .hasSteadyStateData(true)
                .steadyStateCoveragePercent(60.0)
                .cpkPass(true)
                .stdDevPass(true)
                .segments(segments)
                .build();

        assertThat(qualification.evaluate(ctx).status()).isEqualTo(VerdictStatus.PASS);
    }

    // ── Rejestr ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-VP-009: Rejestr wybiera politykę per tryb; null → fallback CHARACTERIZATION")
    void tc_vp_009_registrySelectsPolicy() {
        VerdictPolicyRegistry registry = new VerdictPolicyRegistry(
                List.of(qualification, characterization, monitoring));

        assertThat(registry.forMode(RunMode.QUALIFICATION)).isSameAs(qualification);
        assertThat(registry.forMode(RunMode.CHARACTERIZATION)).isSameAs(characterization);
        assertThat(registry.forMode(RunMode.MONITORING)).isSameAs(monitoring);
        assertThat(registry.forMode(null)).isSameAs(characterization);
        assertThatCode(() -> registry.forMode(null)).doesNotThrowAnyException();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private VerdictContext contextWithExcursionInSteady() {
        List<MeasurementSegment> segments = List.of(
                steady(T0, T0.plusHours(10)),
                segment(SegmentType.EXCURSION, T0.plusHours(3), T0.plusHours(3).plusMinutes(45)));
        return VerdictContext.builder()
                .hasSteadyStateData(true)
                .steadyStateCoveragePercent(85.0)
                .cpkPass(true)
                .stdDevPass(true)
                .segments(segments)
                .build();
    }

    private List<MeasurementSegment> steadyOnlySegments() {
        return List.of(steady(T0, T0.plusHours(10)));
    }

    private MeasurementSegment steady(LocalDateTime from, LocalDateTime to) {
        return segment(SegmentType.STEADY_STATE, from, to);
    }

    private MeasurementSegment segment(SegmentType type, LocalDateTime from, LocalDateTime to) {
        return MeasurementSegment.builder()
                .type(type)
                .fromTimestamp(from)
                .toTimestamp(to)
                .confidence(0.9)
                .accepted(true)
                .build();
    }
}
