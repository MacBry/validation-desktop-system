package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generator hipotez przyczynowych (DP-001 §4.6, Faza 5).
 * <p>
 * Dla każdego wykrytego zdarzenia (lub grupy zdarzeń nakładających się czasowo)
 * generuje zdanie sterowane policzonymi cechami sygnału — czas, amplituda,
 * czas do szczytu, czas powrotu, zaangażowane czujniki — zamiast szablonowego
 * akapitu hipotez. Przykład:
 * <pre>
 * 20:32 — wzrost o +14,0°C w 6 min na pozycjach G-TP, G-TL; powrót w 40 min
 * — wzorzec zgodny z otwarciem drzwi (pewność 0,92).
 * </pre>
 * Klasa bezstanowa i deterministyczna (DP-001 §4.2) — bez ML, wyłącznie cechy
 * policzalne z danych; ten sam przebieg zawsze daje te same zdania.
 */
public class CausalHypothesisGenerator {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    /**
     * Generuje listę hipotez przyczynowych dla zdarzeń — po jednym zdaniu na
     * grupę zdarzeń nakładających się czasowo tego samego typu, chronologicznie.
     *
     * @param eventSegments segmenty typu zdarzeniowego (DEFROST, DOOR_EVENT,
     *                      EXCURSION, SETPOINT_CHANGE) z podpiętymi seriami
     * @return zdania hipotez; pusta lista gdy brak zdarzeń
     */
    public List<String> generateHypotheses(List<MeasurementSegment> eventSegments) {
        if (eventSegments == null || eventSegments.isEmpty()) {
            return List.of();
        }

        List<List<MeasurementSegment>> groups = groupOverlappingSameType(eventSegments);
        List<String> hypotheses = new ArrayList<>(groups.size());
        for (List<MeasurementSegment> group : groups) {
            hypotheses.add(describeGroup(group));
        }
        return hypotheses;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Grupowanie
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Grupuje segmenty tego samego typu nakładające się czasowo (tranzytywnie) —
     * jedno fizyczne zdarzenie widziane przez wiele czujników to jedna hipoteza.
     */
    private List<List<MeasurementSegment>> groupOverlappingSameType(List<MeasurementSegment> segments) {
        List<MeasurementSegment> sorted = segments.stream()
                .sorted(Comparator.comparing(MeasurementSegment::getFromTimestamp))
                .collect(Collectors.toList());

        List<List<MeasurementSegment>> groups = new ArrayList<>();
        for (MeasurementSegment seg : sorted) {
            List<MeasurementSegment> target = null;
            for (List<MeasurementSegment> group : groups) {
                if (group.get(0).getType() == seg.getType() && overlapsAny(group, seg)) {
                    target = group;
                    break;
                }
            }
            if (target != null) {
                target.add(seg);
            } else {
                List<MeasurementSegment> fresh = new ArrayList<>();
                fresh.add(seg);
                groups.add(fresh);
            }
        }
        return groups;
    }

    private boolean overlapsAny(List<MeasurementSegment> group, MeasurementSegment seg) {
        for (MeasurementSegment other : group) {
            if (!seg.getToTimestamp().isBefore(other.getFromTimestamp())
                    && !other.getToTimestamp().isBefore(seg.getFromTimestamp())) {
                return true;
            }
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Opis grupy — zdanie hipotezy
    // ──────────────────────────────────────────────────────────────────────────

    private String describeGroup(List<MeasurementSegment> group) {
        MeasurementSegment first = group.stream()
                .min(Comparator.comparing(MeasurementSegment::getFromTimestamp))
                .orElseThrow();

        // Cechy sygnału z segmentu o największej amplitudzie w grupie
        SignalFeatures features = group.stream()
                .map(this::computeFeatures)
                .filter(f -> f != null)
                .max(Comparator.comparingDouble(f -> Math.abs(f.deltaT)))
                .orElse(null);

        String positions = group.stream()
                .map(s -> s.getSeries() != null ? s.getSeries().getGridPosition() : null)
                .filter(p -> p != null)
                .map(CausalHypothesisGenerator::shortCode)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().collect(Collectors.joining(", "));

        StringBuilder sb = new StringBuilder();
        sb.append(first.getFromTimestamp().format(DATE_TIME_FMT)).append(" — ");

        if (features != null) {
            sb.append(String.format(Locale.forLanguageTag("pl"),
                    "%s o %+.1f°C w %d min", features.deltaT >= 0 ? "wzrost" : "spadek",
                    features.deltaT, Math.max(features.timeToPeakMinutes, 1)));
        } else {
            sb.append("zdarzenie");
        }

        if (!positions.isEmpty()) {
            sb.append(group.size() > 1 ? " na pozycjach " : " na pozycji ").append(positions);
        }

        long durationMin = first.durationMinutes();
        if (features != null && features.returned) {
            sb.append(String.format("; powrót do poziomu wyjściowego w %d min", durationMin));
        } else {
            sb.append(String.format("; czas trwania %d min", durationMin));
        }

        sb.append(" — ").append(patternConclusion(first));
        sb.append(verificationSuffix(first));
        sb.append(".");
        return sb.toString();
    }

    private String patternConclusion(MeasurementSegment seg) {
        Double conf = seg.getConfidence();
        String confStr = conf != null
                ? String.format(Locale.forLanguageTag("pl"), " (pewność %.2f)", conf) : "";
        return switch (seg.getType()) {
            case DOOR_EVENT -> "wzorzec zgodny z otwarciem drzwi" + confStr;
            case DEFROST -> "wzorzec zgodny z cyklem odszraniania" + confStr;
            case SETPOINT_CHANGE ->
                    "trwała zmiana poziomu — zgodna ze zmianą nastawy lub trybem wymuszonym (np. fastcooling)" + confStr;
            case EXCURSION ->
                    "wzorzec nie odpowiada znanym zdarzeniom — wymaga wyjaśnienia przez operatora" + confStr;
            default -> "zmiana reżimu pracy" + confStr;
        };
    }

    /** Dopisek statusu weryfikacji human-in-the-loop (Faza 4). */
    private String verificationSuffix(MeasurementSegment seg) {
        if (seg.getConfirmedBy() == null) {
            return "";
        }
        return seg.isAccepted()
                ? " [potwierdzone: " + seg.getConfirmedBy() + "]"
                : " [ODRZUCONE przez: " + seg.getConfirmedBy() + "]";
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cechy sygnału
    // ──────────────────────────────────────────────────────────────────────────

    private record SignalFeatures(double deltaT, long timeToPeakMinutes, boolean returned) {
    }

    /**
     * Liczy cechy sygnału w oknie segmentu: baseline = pierwszy punkt okna,
     * szczyt = ekstremum o największym odchyleniu od baseline, powrót = czy
     * ostatni punkt okna wrócił w pobliże baseline (±1,0°C).
     */
    private SignalFeatures computeFeatures(MeasurementSegment seg) {
        if (seg.getSeries() == null || seg.getSeries().getMeasurements() == null) {
            return null;
        }
        List<ThermoMeasurementPoint> window = seg.getSeries().getMeasurements().stream()
                .filter(p -> p.getTimestampLocal() != null && seg.contains(p.getTimestampLocal()))
                .sorted(Comparator.comparing(ThermoMeasurementPoint::getTimestampLocal))
                .collect(Collectors.toList());
        if (window.size() < 2) {
            return null;
        }

        double baseline = window.get(0).getRawCelsius();
        ThermoMeasurementPoint peak = window.get(0);
        double maxAbsDelta = 0.0;
        for (ThermoMeasurementPoint p : window) {
            double delta = Math.abs(p.getRawCelsius() - baseline);
            if (delta > maxAbsDelta) {
                maxAbsDelta = delta;
                peak = p;
            }
        }
        if (maxAbsDelta < 1e-9) {
            return null;
        }

        double deltaT = peak.getRawCelsius() - baseline;
        long timeToPeak = Duration.between(
                window.get(0).getTimestampLocal(), peak.getTimestampLocal()).toMinutes();
        boolean returned = Math.abs(
                window.get(window.size() - 1).getRawCelsius() - baseline) <= 1.0;

        return new SignalFeatures(deltaT, timeToPeak, returned);
    }

    // ──────────────────────────────────────────────────────────────────────────

    /** Krótki kod pozycji zgodny z konwencją raportów (G-TP, D-PL, ...). */
    static String shortCode(GridPosition pos) {
        return switch (pos) {
            case TOP_FRONT_LEFT -> "G-PL";
            case TOP_FRONT_RIGHT -> "G-PP";
            case TOP_BACK_LEFT -> "G-TL";
            case TOP_BACK_RIGHT -> "G-TP";
            case BOTTOM_FRONT_LEFT -> "D-PL";
            case BOTTOM_FRONT_RIGHT -> "D-PP";
            case BOTTOM_BACK_LEFT -> "D-TL";
            case BOTTOM_BACK_RIGHT -> "D-TP";
        };
    }
}
