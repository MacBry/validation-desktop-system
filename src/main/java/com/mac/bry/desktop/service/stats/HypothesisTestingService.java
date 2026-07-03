package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.AnovaResult;
import com.mac.bry.desktop.dto.stats.TostResult;
import com.mac.bry.desktop.dto.stats.WelchAnovaResult;
import com.mac.bry.desktop.dto.stats.GamesHowellResult;
import com.mac.bry.desktop.dto.stats.DunnResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class HypothesisTestingService {

    // --- Klasyczna aprobata normalnego CDF (metoda błędu Erf) ---
    private static double erf(double x) {
        // Przybliżenie Winitzkiego (dokładność 10^-4)
        double a = 0.147;
        double x2 = x * x;
        double term = x2 * (4.0 / Math.PI + a * x2) / (1.0 + a * x2);
        double val = Math.sqrt(1.0 - Math.exp(-term));
        return x < 0 ? -val : val;
    }

    public static double normalCdf(double z) {
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }

    // --- Przybliżenie dystrybucji F (Wilson-Hilferty) ---
    public static double fCdf(double f, double df1, double df2) {
        if (f <= 0) return 0.0;
        double f3 = Math.pow(f, 1.0 / 3.0);
        double term1 = 2.0 / (9.0 * df1);
        double term2 = 2.0 / (9.0 * df2);
        double num = (1.0 - term2) * f3 - (1.0 - term1);
        double den = Math.sqrt(term2 * f3 * f3 + term1);
        double z = num / den;
        return normalCdf(z);
    }

    // --- Przybliżenie dystrybucji Chi-Kwadrat (Wilson-Hilferty) ---
    public static double chiSquareCdf(double x, double df) {
        if (x <= 0) return 0.0;
        if (df == 2.0) {
            return 1.0 - Math.exp(-x / 2.0); // Dokładny wzór dla df=2
        }
        double term = 2.0 / (9.0 * df);
        double num = Math.pow(x / df, 1.0 / 3.0) - (1.0 - term);
        double den = Math.sqrt(term);
        double z = num / den;
        return normalCdf(z);
    }

    // --- Lanczos logGamma approximation ---
    public static double logGamma(double x) {
        double[] coef = {
            1.000000000190015,
            76.18009172947146,
            -86.50532032941677,
            24.01409824083091,
            -1.231739572450155,
            0.1208650973866179e-2,
            -0.5395239384953e-5
        };
        double temp = x + 5.5;
        temp -= (x + 0.5) * Math.log(temp);
        double ser = coef[0];
        for (int i = 1; i < coef.length; i++) {
            ser += coef[i] / (x + i);
        }
        return -temp + Math.log(2.5066282746310005 * ser / x);
    }

    private static double phi(double x) {
        return Math.exp(-x * x / 2.0) / Math.sqrt(2.0 * Math.PI);
    }

    private static double pRangeNormal(double w, int k) {
        if (w <= 0.0) return 0.0;
        double startU = -8.0;
        double endU = 8.0;
        int steps = 200;
        double stepU = (endU - startU) / steps;
        double sum = 0.0;
        for (int i = 0; i <= steps; i++) {
            double u = startU + i * stepU;
            double phiVal = phi(u);
            double cdfDiff = normalCdf(u) - normalCdf(u - w);
            if (cdfDiff < 0.0) cdfDiff = 0.0;
            if (cdfDiff > 1.0) cdfDiff = 1.0;
            double term = k * phiVal * Math.pow(cdfDiff, k - 1);
            
            double weight = stepU;
            if (i > 0 && i < steps) {
                weight *= (i % 2 == 0) ? 2.0 : 4.0;
            }
            sum += weight * term;
        }
        double result = sum / 3.0;
        return Math.min(1.0, Math.max(0.0, result));
    }

    public static double pTukey(double q, int k, double df) {
        if (q <= 0.0) return 1.0;
        if (k < 2 || df < 1.0) return 1.0;
        
        double pRange;
        if (df > 1000.0) {
            pRange = pRangeNormal(q, k);
        } else {
            double startS = Math.max(0.01, 1.0 - 5.0 / Math.sqrt(2.0 * df));
            double endS = 1.0 + 5.0 / Math.sqrt(2.0 * df);
            int stepsS = 50;
            double stepS = (endS - startS) / stepsS;
            double sum = 0.0;
            double totalWeight = 0.0;
            
            for (int i = 0; i <= stepsS; i++) {
                double s = startS + i * stepS;
                double logDensity = Math.log(2) + (df / 2.0) * Math.log(df / 2.0) - logGamma(df / 2.0) + (df - 1.0) * Math.log(s) - (df * s * s) / 2.0;
                double density = Math.exp(logDensity);
                
                double weight = stepS;
                if (i > 0 && i < stepsS) {
                    weight *= (i % 2 == 0) ? 2.0 : 4.0;
                }
                
                double pVal = pRangeNormal(q * s, k);
                sum += weight * density * pVal;
                totalWeight += weight * density;
            }
            pRange = sum / totalWeight;
        }
        double pVal = 1.0 - pRange;
        return Math.min(1.0, Math.max(0.0, pVal));
    }

    // --- Zaimplementowane Testy ---

    /**
     * Test Równoważności TOST (Two One-Sided Tests)
     * Wykazuje, czy różnica średnich mieści się w granicach [-theta, theta].
     */
    public TostResult performTostEquivalence(double[] sample1, double[] sample2, double theta) {
        if (sample1 == null || sample2 == null || sample1.length < 2 || sample2.length < 2) {
            return new TostResult(false, 1.0, 1.0, theta, 0.0);
        }

        int n1 = sample1.length;
        int n2 = sample2.length;

        double mean1 = SensorStatsEngine.calculateMean(sample1);
        double mean2 = SensorStatsEngine.calculateMean(sample2);
        double var1 = SensorStatsEngine.calculateVariance(sample1);
        double var2 = SensorStatsEngine.calculateVariance(sample2);

        double meanDiff = mean1 - mean2;

        // Pooled standard deviation
        double pooledVar = ((n1 - 1) * var1 + (n2 - 1) * var2) / (n1 + n2 - 2);
        double pooledSd = Math.sqrt(pooledVar);
        double se = pooledSd * Math.sqrt(1.0 / n1 + 1.0 / n2);

        if (se == 0.0) {
            return new TostResult(Math.abs(meanDiff) <= theta, 0.0, 0.0, theta, meanDiff);
        }

        // Statystyki testowe
        double t1 = (meanDiff - (-theta)) / se;
        double t2 = (meanDiff - theta) / se;

        // Dla dużych prób z logera (N > 30) rozkład t zbiega do normalnego
        double p1 = 1.0 - normalCdf(t1); // H1: diff <= -theta
        double p2 = normalCdf(t2);        // H2: diff >= theta

        // Hipoteza zerowa (brak równoważności) jest odrzucana, gdy p1 < 0.05 i p2 < 0.05
        boolean equivalent = p1 < 0.05 && p2 < 0.05;

        return new TostResult(equivalent, p1, p2, theta, meanDiff);
    }

    /**
     * Jednoczynnikowa analiza wariancji (ANOVA)
     */
    public AnovaResult performAnova(List<double[]> samples) {
        if (samples == null || samples.isEmpty()) {
            return new AnovaResult(false, 1.0, 0.0, 0, 0);
        }

        int k = samples.size();
        int totalN = 0;
        double grandSum = 0.0;

        for (double[] s : samples) {
            totalN += s.length;
            for (double val : s) {
                grandSum += val;
            }
        }

        if (totalN <= k) {
            return new AnovaResult(false, 1.0, 0.0, 0, 0);
        }

        double grandMean = grandSum / totalN;

        double ssb = 0.0;
        double ssw = 0.0;

        for (double[] s : samples) {
            double groupMean = SensorStatsEngine.calculateMean(s);
            ssb += s.length * Math.pow(groupMean - grandMean, 2);
            for (double val : s) {
                ssw += Math.pow(val - groupMean, 2);
            }
        }

        int dfBetween = k - 1;
        int dfWithin = totalN - k;

        double msb = ssb / dfBetween;
        double msw = ssw / dfWithin;

        double fValue = msw == 0.0 ? 0.0 : msb / msw;
        double pValue = 1.0 - fCdf(fValue, dfBetween, dfWithin);

        return new AnovaResult(pValue < 0.05, pValue, fValue, dfBetween, dfWithin);
    }

    /**
     * Test Kruskala-Wallisa (Nieparametryczna alternatywa dla ANOVA)
     * Zwraca wartość p-value.
     */
    public double performKruskalWallis(List<double[]> samples) {
        if (samples == null || samples.size() < 2) return 1.0;

        // Połącz wszystkie elementy zachowując informację o grupie
        List<RankItem> allItems = new ArrayList<>();
        int k = samples.size();
        for (int g = 0; g < k; g++) {
            for (double val : samples.get(g)) {
                allItems.add(new RankItem(val, g));
            }
        }

        int totalN = allItems.size();
        if (totalN == 0) return 1.0;

        // Posortuj po wartościach pomiaru
        allItems.sort((a, b) -> Double.compare(a.value, b.value));

        // Przydziel rangi z uwzględnieniem remisów
        double[] ranks = new double[totalN];
        int i = 0;
        while (i < totalN) {
            int j = i + 1;
            while (j < totalN && allItems.get(j).value == allItems.get(i).value) {
                j++;
            }
            double rankSum = 0;
            for (int r = i; r < j; r++) {
                rankSum += (r + 1); // 1-indexed ranks
            }
            double avgRank = rankSum / (j - i);
            for (int r = i; r < j; r++) {
                ranks[r] = avgRank;
                allItems.get(r).rank = avgRank;
            }
            i = j;
        }

        // Sumuj rangi dla każdej grupy
        double[] groupRankSums = new double[k];
        int[] groupSizes = new int[k];
        for (RankItem item : allItems) {
            groupRankSums[item.group] += item.rank;
            groupSizes[item.group]++;
        }

        // Oblicz H
        double sumSquares = 0.0;
        for (int g = 0; g < k; g++) {
            if (groupSizes[g] > 0) {
                sumSquares += Math.pow(groupRankSums[g], 2) / groupSizes[g];
            }
        }

        double h = (12.0 / (totalN * (totalN + 1.0))) * sumSquares - 3.0 * (totalN + 1.0);

        // Korekta dla remisów (ties)
        double tieCorrection = 1.0;
        // Znajdź grupy powtórzeń
        int countTies = 0;
        double sumTieTerms = 0.0;
        i = 0;
        while (i < totalN) {
            int j = i + 1;
            while (j < totalN && allItems.get(j).value == allItems.get(i).value) {
                j++;
            }
            int tieCount = j - i;
            if (tieCount > 1) {
                sumTieTerms += (Math.pow(tieCount, 3) - tieCount);
            }
            i = j;
        }
        if (totalN > 1) {
            tieCorrection = 1.0 - (sumTieTerms / (Math.pow(totalN, 3) - totalN));
        }

        if (tieCorrection > 0) {
            h /= tieCorrection;
        }

        // H ma rozkład Chi-Kwadrat z df = k - 1
        return 1.0 - chiSquareCdf(h, k - 1);
    }

    public double performFTest(double[] sample1, double[] sample2) {
        if (sample1 == null || sample2 == null || sample1.length < 2 || sample2.length < 2) {
            return 1.0;
        }
        double var1 = SensorStatsEngine.calculateVariance(sample1);
        double var2 = SensorStatsEngine.calculateVariance(sample2);

        if (var1 == 0.0 && var2 == 0.0) return 1.0;
        if (var1 == 0.0 || var2 == 0.0) return 0.0;

        double f = var1 >= var2 ? var1 / var2 : var2 / var1;
        double df1 = var1 >= var2 ? sample1.length - 1 : sample2.length - 1;
        double df2 = var1 >= var2 ? sample2.length - 1 : sample1.length - 1;

        double p = 2.0 * (1.0 - fCdf(f, df1, df2));
        return Math.min(p, 1.0);
    }

    /**
     * Test normalności Jarque-Bera (zoptymalizowany dla prób N > 30 z logerów)
     * Zwraca p-value.
     */
    public double performJarqueBera(double[] sample) {
        if (sample == null || sample.length < 5) return 0.0;
        double skew = SensorStatsEngine.calculateSkewness(sample);
        double kurt = SensorStatsEngine.calculateKurtosis(sample); // to już jest excess kurtosis (kurtoza - 3)

        int n = sample.length;
        double jb = (n / 6.0) * (skew * skew + (kurt * kurt) / 4.0);

        // JB ma rozkład Chi-Kwadrat z df=2
        return 1.0 - chiSquareCdf(jb, 2);
    }

    /**
     * Welch's ANOVA (Jednokierunkowa analiza wariancji dla nierównych wariancji)
     */
    public WelchAnovaResult performWelchAnova(List<double[]> samples) {
        if (samples == null || samples.size() < 2) {
            return new WelchAnovaResult(false, 1.0, 0.0, 0.0, 0.0);
        }

        int k = samples.size();
        double[] means = new double[k];
        double[] vars = new double[k];
        double[] weights = new double[k];
        int[] ns = new int[k];
        double sumWeights = 0.0;

        for (int i = 0; i < k; i++) {
            double[] s = samples.get(i);
            if (s == null || s.length < 2) {
                return new WelchAnovaResult(false, 1.0, 0.0, 0.0, 0.0);
            }
            ns[i] = s.length;
            means[i] = SensorStatsEngine.calculateMean(s);
            double v = SensorStatsEngine.calculateVariance(s);
            if (v <= 0.0) {
                v = 1e-9;
            }
            vars[i] = v;
            weights[i] = ns[i] / v;
            sumWeights += weights[i];
        }

        double weightedMean = 0.0;
        for (int i = 0; i < k; i++) {
            weightedMean += weights[i] * means[i];
        }
        weightedMean /= sumWeights;

        double lambda = 0.0;
        for (int i = 0; i < k; i++) {
            lambda += weights[i] * Math.pow(means[i] - weightedMean, 2);
        }
        lambda /= (k - 1);

        double qSum = 0.0;
        for (int i = 0; i < k; i++) {
            qSum += (1.0 / (ns[i] - 1.0)) * Math.pow(1.0 - weights[i] / sumWeights, 2);
        }
        double qValue = (3.0 * qSum) / (k * k - 1.0);

        double fValue = lambda / (1.0 + (2.0 * (k - 2.0) / 3.0) * qValue);
        double df1 = k - 1.0;
        double df2 = 1.0 / qValue;

        double pValue = 1.0 - fCdf(fValue, df1, df2);
        return new WelchAnovaResult(pValue < 0.05, pValue, fValue, df1, df2);
    }

    /**
     * Games-Howell post-hoc test
     */
    public List<GamesHowellResult> performGamesHowell(List<double[]> samples) {
        List<GamesHowellResult> results = new ArrayList<>();
        if (samples == null || samples.size() < 2) {
            return results;
        }

        int k = samples.size();
        double[] means = new double[k];
        double[] vars = new double[k];
        int[] ns = new int[k];

        for (int i = 0; i < k; i++) {
            double[] s = samples.get(i);
            if (s == null || s.length < 2) {
                return results;
            }
            ns[i] = s.length;
            means[i] = SensorStatsEngine.calculateMean(s);
            double v = SensorStatsEngine.calculateVariance(s);
            if (v <= 0.0) {
                v = 1e-9;
            }
            vars[i] = v;
        }

        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                double meanDiff = Math.abs(means[i] - means[j]);
                double vi_ni = vars[i] / ns[i];
                double vj_nj = vars[j] / ns[j];

                double se = Math.sqrt(0.5 * (vi_ni + vj_nj));
                double tValue = meanDiff / Math.sqrt(vi_ni + vj_nj);
                double qValue = meanDiff / se;

                double num = Math.pow(vi_ni + vj_nj, 2);
                double den = Math.pow(vi_ni, 2) / (ns[i] - 1.0) + Math.pow(vj_nj, 2) / (ns[j] - 1.0);
                double df = num / den;

                double pValue = pTukey(qValue, k, df);
                results.add(new GamesHowellResult(i, j, meanDiff, se, df, tValue, qValue, pValue, pValue < 0.05));
            }
        }

        return results;
    }

    /**
     * Dunn's post-hoc test
     */
    public List<DunnResult> performDunnTest(List<double[]> samples) {
        List<DunnResult> results = new ArrayList<>();
        if (samples == null || samples.size() < 2) {
            return results;
        }

        int k = samples.size();
        
        List<RankItem> allItems = new ArrayList<>();
        for (int g = 0; g < k; g++) {
            for (double val : samples.get(g)) {
                allItems.add(new RankItem(val, g));
            }
        }

        int totalN = allItems.size();
        if (totalN == 0) {
            return results;
        }

        allItems.sort((a, b) -> Double.compare(a.value, b.value));

        int i = 0;
        while (i < totalN) {
            int j = i + 1;
            while (j < totalN && allItems.get(j).value == allItems.get(i).value) {
                j++;
            }
            double rankSum = 0;
            for (int r = i; r < j; r++) {
                rankSum += (r + 1);
            }
            double avgRank = rankSum / (j - i);
            for (int r = i; r < j; r++) {
                allItems.get(r).rank = avgRank;
            }
            i = j;
        }

        double[] groupRankSums = new double[k];
        int[] groupSizes = new int[k];
        for (RankItem item : allItems) {
            groupRankSums[item.group] += item.rank;
            groupSizes[item.group]++;
        }

        double[] meanRanks = new double[k];
        for (int g = 0; g < k; g++) {
            if (groupSizes[g] > 0) {
                meanRanks[g] = groupRankSums[g] / groupSizes[g];
            }
        }

        double sumTieTerms = 0.0;
        i = 0;
        while (i < totalN) {
            int j = i + 1;
            while (j < totalN && allItems.get(j).value == allItems.get(i).value) {
                j++;
            }
            int tieCount = j - i;
            if (tieCount > 1) {
                sumTieTerms += (Math.pow(tieCount, 3) - tieCount);
            }
            i = j;
        }

        double tiesCorrection = 0.0;
        if (totalN > 1) {
            tiesCorrection = sumTieTerms / (12.0 * (totalN - 1.0));
        }

        double numComparisons = k * (k - 1) / 2.0;

        for (int g1 = 0; g1 < k; g1++) {
            for (int g2 = g1 + 1; g2 < k; g2++) {
                int n1 = groupSizes[g1];
                int n2 = groupSizes[g2];

                if (n1 == 0 || n2 == 0) {
                    continue;
                }

                double meanRankDiff = Math.abs(meanRanks[g1] - meanRanks[g2]);
                
                double factor1 = (totalN * (totalN + 1.0)) / 12.0 - tiesCorrection;
                double factor2 = 1.0 / n1 + 1.0 / n2;
                double se = Math.sqrt(factor1 * factor2);

                double zValue = se > 0.0 ? meanRankDiff / se : 0.0;
                double pValue = 2.0 * (1.0 - normalCdf(zValue));
                double adjustedPValue = Math.min(pValue * numComparisons, 1.0);

                results.add(new DunnResult(g1, g2, meanRankDiff, zValue, pValue, adjustedPValue, adjustedPValue < 0.05));
            }
        }

        return results;
    }

    private static class RankItem {
        double value;
        int group;
        double rank;

        RankItem(double value, int group) {
            this.value = value;
            this.group = group;
        }
    }
}
