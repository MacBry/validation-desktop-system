package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.AnovaResult;
import com.mac.bry.desktop.dto.stats.TostResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HypothesisTestingServiceTest {

    private final HypothesisTestingService testingService = new HypothesisTestingService();

    @Test
    @DisplayName("should compute correct Normal CDF approximation")
    void shouldComputeCorrectNormalCdf() {
        // z = 0.0 -> cdf = 0.5
        assertThat(HypothesisTestingService.normalCdf(0.0)).isCloseTo(0.5, within(0.001));
        // z = 1.96 -> cdf ~ 0.975
        assertThat(HypothesisTestingService.normalCdf(1.96)).isCloseTo(0.975, within(0.005));
        // z = -1.96 -> cdf ~ 0.025
        assertThat(HypothesisTestingService.normalCdf(-1.96)).isCloseTo(0.025, within(0.005));
    }

    @Test
    @DisplayName("should perform TOST equivalence testing correctly")
    void shouldPerformTostCorrectly() {
        double[] sample1 = { 5.0, 5.1, 4.9, 5.0, 5.2, 4.8, 5.0, 5.0 };
        double[] sample2 = { 5.0, 5.0, 5.0, 5.1, 4.9, 5.0, 5.1, 4.9 };

        // Test with equivalence interval theta = 0.5
        TostResult result = testingService.performTostEquivalence(sample1, sample2, 0.5);
        assertThat(result.isEquivalent()).isTrue();
        assertThat(result.getPValue1()).isLessThan(0.05);
        assertThat(result.getPValue2()).isLessThan(0.05);

        // Test with extremely narrow equivalence interval theta = 0.01 (should fail)
        TostResult resultFail = testingService.performTostEquivalence(sample1, sample2, 0.01);
        assertThat(resultFail.isEquivalent()).isFalse();
    }

    @Test
    @DisplayName("should perform ANOVA on identical and distinct groups")
    void shouldPerformAnovaCorrectly() {
        double[] g1 = { 5.0, 5.1, 4.9, 5.0 };
        double[] g2 = { 5.0, 5.2, 4.8, 5.0 };
        double[] g3 = { 5.1, 5.0, 5.0, 4.9 };

        AnovaResult resultSame = testingService.performAnova(List.of(g1, g2, g3));
        // means are close, so we shouldn't reject H0
        assertThat(resultSame.isSignificantDifference()).isFalse();
        assertThat(resultSame.getPValue()).isGreaterThan(0.05);

        double[] gDiff = { 8.0, 8.2, 7.8, 8.0 };
        AnovaResult resultDiff = testingService.performAnova(List.of(g1, g2, gDiff));
        // gDiff is completely different, so we should reject H0
        assertThat(resultDiff.isSignificantDifference()).isTrue();
        assertThat(resultDiff.getPValue()).isLessThan(0.05);
    }

    @Test
    @DisplayName("should perform Kruskal-Wallis test correctly")
    void shouldPerformKruskalWallisCorrectly() {
        double[] g1 = { 1.0, 2.0, 3.0, 4.0, 5.0 };
        double[] g2 = { 1.1, 2.1, 3.1, 4.1, 5.1 };
        double[] gDiff = { 10.0, 11.0, 12.0, 13.0, 14.0 };

        double pSame = testingService.performKruskalWallis(List.of(g1, g2));
        assertThat(pSame).isGreaterThan(0.05);

        double pDiff = testingService.performKruskalWallis(List.of(g1, gDiff));
        assertThat(pDiff).isLessThan(0.05);
    }

    @Test
    @DisplayName("should perform F-test for equal/unequal variances")
    void shouldPerformFTestCorrectly() {
        double[] sample1 = { 5.0, 5.0, 5.0, 5.0, 5.0 };
        double[] sample2 = { 5.0, 10.0, 5.0, 0.0, 5.0 }; // high variance

        double p = testingService.performFTest(sample1, sample2);
        // variances are completely different, so p should be very small
        assertThat(p).isLessThan(0.05);
    }

    @Test
    @DisplayName("should perform Jarque-Bera normality test correctly")
    void shouldPerformJarqueBeraNormalityTest() {
        // Normal-like data
        double[] normalData = { 5.0, 5.1, 4.9, 5.0, 5.2, 4.8, 5.0, 5.1, 4.9, 5.0, 5.0, 4.9, 5.1, 5.0, 5.0, 5.2, 4.8, 5.0, 5.0 };
        double pNormal = testingService.performJarqueBera(normalData);
        assertThat(pNormal).isGreaterThan(0.05);

        // Skewed data
        double[] skewedData = { 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 10.0, 15.0, 20.0, 25.0 };
        double pSkewed = testingService.performJarqueBera(skewedData);
        assertThat(pSkewed).isLessThan(0.05);
    }

    @Test
    @DisplayName("should approximate Tukey p-value correctly")
    void shouldApproximateTukeyCorrectly() {
        // Critical value q = 3.877 for k=3, df=10 at alpha=0.05
        double p1 = HypothesisTestingService.pTukey(3.877, 3, 10);
        assertThat(p1).isCloseTo(0.05, within(0.01));

        // Critical value q = 3.314 for k=3, df=9999 (infinity) at alpha=0.05
        double p2 = HypothesisTestingService.pTukey(3.314, 3, 9999);
        assertThat(p2).isCloseTo(0.05, within(0.01));

        // Boundary cases
        assertThat(HypothesisTestingService.pTukey(0.0, 3, 10)).isEqualTo(1.0);
        assertThat(HypothesisTestingService.pTukey(20.0, 3, 10)).isCloseTo(0.0, within(0.001));
    }

    @Test
    @DisplayName("should perform Welch's ANOVA on equal and unequal groups")
    void shouldPerformWelchAnovaCorrectly() {
        // Similar groups
        double[] g1 = { 5.0, 5.1, 4.9, 5.0, 5.2 };
        double[] g2 = { 5.0, 4.8, 5.2, 5.1, 4.9 };
        double[] g3 = { 5.1, 5.0, 5.0, 4.9, 5.0 };

        var resultSame = testingService.performWelchAnova(List.of(g1, g2, g3));
        assertThat(resultSame.isSignificantDifference()).isFalse();
        assertThat(resultSame.getPValue()).isGreaterThan(0.05);

        // Unequal groups with different variances
        double[] gDiff = { 10.0, 10.5, 9.5, 10.0, 10.1 };
        var resultDiff = testingService.performWelchAnova(List.of(g1, g2, gDiff));
        assertThat(resultDiff.isSignificantDifference()).isTrue();
        assertThat(resultDiff.getPValue()).isLessThan(0.05);
    }

    @Test
    @DisplayName("should perform Games-Howell post-hoc test correctly")
    void shouldPerformGamesHowellCorrectly() {
        double[] g1 = { 5.0, 5.1, 4.9, 5.0, 5.2 };
        double[] g2 = { 5.0, 4.8, 5.2, 5.1, 4.9 };
        double[] gDiff = { 10.0, 10.5, 9.5, 10.0, 10.1 };

        var results = testingService.performGamesHowell(List.of(g1, g2, gDiff));
        // k=3 implies 3 comparisons: 0-1, 0-2, 1-2
        assertThat(results).hasSize(3);

        // g1 (0) vs g2 (1) should not be significant
        var comp01 = results.stream().filter(r -> r.getGroupA() == 0 && r.getGroupB() == 1).findFirst().orElseThrow();
        assertThat(comp01.isSignificant()).isFalse();

        // g1 (0) vs gDiff (2) should be significant
        var comp02 = results.stream().filter(r -> r.getGroupA() == 0 && r.getGroupB() == 2).findFirst().orElseThrow();
        assertThat(comp02.isSignificant()).isTrue();
        assertThat(comp02.getPValue()).isLessThan(0.05);
    }

    @Test
    @DisplayName("should perform Dunn's post-hoc test correctly")
    void shouldPerformDunnTestCorrectly() {
        double[] g1 = { 1.0, 2.0, 3.0, 4.0, 5.0 };
        double[] g2 = { 1.1, 2.1, 3.1, 4.1, 5.1 };
        double[] gDiff = { 20.0, 21.0, 22.0, 23.0, 24.0 };

        var results = testingService.performDunnTest(List.of(g1, g2, gDiff));
        assertThat(results).hasSize(3);

        // g1 (0) vs g2 (1) should not be significant
        var comp01 = results.stream().filter(r -> r.getGroupA() == 0 && r.getGroupB() == 1).findFirst().orElseThrow();
        assertThat(comp01.isSignificant()).isFalse();

        // g1 (0) vs gDiff (2) should be significant
        var comp02 = results.stream().filter(r -> r.getGroupA() == 0 && r.getGroupB() == 2).findFirst().orElseThrow();
        assertThat(comp02.isSignificant()).isTrue();
        assertThat(comp02.getAdjustedPValue()).isLessThan(0.05);
    }
}
