package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.repository.MeasurementSegmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serwis adnotacji segmentów — human-in-the-loop (DP-001 §4.2, Faza 4).
 * <p>
 * Operator przegląda wykryte algorytmicznie segmenty i potwierdza lub odrzuca
 * każdy z nich. Decyzja zapisuje login operatora i timestamp; odrzucone segmenty
 * są wykluczane z obliczeń statystyk warunkowych (przez istniejący filtr
 * {@code accepted} w {@link RegimeAwareStatsService}).
 * <p>
 * Dla segmentów należących do utrwalonych serii (id != null) decyzja jest
 * zapisywana do bazy — pełny ślad audytowy przez Hibernate Envers.
 * Segmenty sesji kreatora (seria jeszcze nieutrwalona) adnotowane są w pamięci;
 * ślad w raporcie PDF zawiera login i timestamp potwierdzenia.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentAnnotationService {

    private final RegimeDetectionService regimeDetectionService;
    private final MeasurementSegmentRepository segmentRepository;

    /**
     * Uruchamia detekcję reżimów dla wszystkich pozycji sesji i wypełnia
     * {@code session.detectedSegmentsMap}. Jeżeli mapa jest już wypełniona
     * (operator przeglądał segmenty), istniejące segmenty są zachowywane —
     * ponowna detekcja nadpisałaby adnotacje operatora.
     *
     * @param session sesja rewalidacji z przypisanymi pozycjami
     * @return mapa segmentów per pozycja (referencja do mapy sesji)
     */
    public Map<GridPosition, List<MeasurementSegment>> detectForSession(RevalidationSession session) {
        if (!session.getDetectedSegmentsMap().isEmpty()) {
            log.debug("SegmentAnnotationService: segmenty już wykryte — zachowuję adnotacje operatora");
            return session.getDetectedSegmentsMap();
        }

        Map<GridPosition, ThermoMeasurementSeries> allChannels = new HashMap<>();
        session.getAssignedPositions().forEach((pos, data) -> {
            if (data != null && data.getSeries() != null) {
                allChannels.put(pos, data.getSeries());
            }
        });

        for (Map.Entry<GridPosition, ThermoMeasurementSeries> entry : allChannels.entrySet()) {
            var detection = regimeDetectionService.detect(entry.getValue(), allChannels);
            session.getDetectedSegmentsMap().put(entry.getKey(), detection.getSegments());
        }
        log.info("SegmentAnnotationService: detekcja zakończona dla {} pozycji", allChannels.size());
        return session.getDetectedSegmentsMap();
    }

    /**
     * Potwierdza segment jako poprawnie wykryty (human-in-the-loop).
     * Zapisuje login operatora i timestamp; utrwala jeśli segment jest w bazie.
     */
    @Transactional
    public void accept(MeasurementSegment segment) {
        annotate(segment, true);
    }

    /**
     * Odrzuca segment jako błędnie wykryty. Odrzucone segmenty są wykluczane
     * z obliczeń statystyk warunkowych i oznaczane w raporcie PDF.
     */
    @Transactional
    public void reject(MeasurementSegment segment) {
        annotate(segment, false);
    }

    private void annotate(MeasurementSegment segment, boolean accepted) {
        String operator = currentOperator();
        segment.setAccepted(accepted);
        segment.setConfirmedBy(operator);
        segment.setConfirmedAt(LocalDateTime.now());

        // Utrwalenie decyzji tylko dla segmentów już zapisanych w bazie —
        // segmenty sesji kreatora żyją w pamięci do czasu zapisu serii
        if (segment.getId() != null) {
            segmentRepository.save(segment);
        }
        log.info("Segment {} [{}] {}: operator={}",
                segment.getId() != null ? segment.getId() : "(in-memory)",
                segment.getType(),
                accepted ? "POTWIERDZONY" : "ODRZUCONY",
                operator);
    }

    private String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
