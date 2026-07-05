package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.regime.DetectionSource;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import com.mac.bry.desktop.repository.MeasurementSegmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testy human-in-the-loop adnotacji segmentów (DP-001 Faza 4).
 */
@ExtendWith(MockitoExtension.class)
class SegmentAnnotationServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 6, 21, 8, 0);

    @InjectMocks
    private SegmentAnnotationService service;

    @Mock
    private RegimeDetectionService regimeDetectionService;

    @Mock
    private MeasurementSegmentRepository segmentRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("TC-ANN-001: Accept zapisuje operatora, timestamp i accepted=true")
    void tc_ann_001_acceptRecordsOperator() {
        authenticateAs("jkowalski");
        MeasurementSegment segment = inMemorySegment(SegmentType.DEFROST);

        service.accept(segment);

        assertThat(segment.isAccepted()).isTrue();
        assertThat(segment.getConfirmedBy()).isEqualTo("jkowalski");
        assertThat(segment.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("TC-ANN-002: Reject ustawia accepted=false — segment wypada ze statystyk warunkowych")
    void tc_ann_002_rejectExcludesSegment() {
        authenticateAs("jkowalski");
        MeasurementSegment segment = inMemorySegment(SegmentType.STEADY_STATE);

        service.reject(segment);

        assertThat(segment.isAccepted()).isFalse();
        assertThat(segment.getConfirmedBy()).isEqualTo("jkowalski");
    }

    @Test
    @DisplayName("TC-ANN-003: Segment in-memory (bez ID) nie jest zapisywany do repozytorium")
    void tc_ann_003_inMemorySegmentNotPersisted() {
        authenticateAs("jkowalski");
        MeasurementSegment segment = inMemorySegment(SegmentType.EXCURSION);

        service.accept(segment);

        verify(segmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-ANN-004: Segment utrwalony (z ID) jest zapisywany — ślad audytowy Envers")
    void tc_ann_004_persistedSegmentSaved() {
        authenticateAs("jkowalski");
        MeasurementSegment segment = inMemorySegment(SegmentType.DOOR_EVENT);
        segment.setId(42L);

        service.reject(segment);

        verify(segmentRepository).save(segment);
    }

    @Test
    @DisplayName("TC-ANN-005: Brak zalogowanego operatora → confirmedBy='system'")
    void tc_ann_005_noAuthenticationFallsBackToSystem() {
        SecurityContextHolder.clearContext();
        MeasurementSegment segment = inMemorySegment(SegmentType.DEFROST);

        service.accept(segment);

        assertThat(segment.getConfirmedBy()).isEqualTo("system");
    }

    @Test
    @DisplayName("TC-ANN-006: detectForSession nie nadpisuje istniejących adnotacji operatora")
    void tc_ann_006_detectPreservesExistingAnnotations() {
        RevalidationSession session = new RevalidationSession();
        MeasurementSegment annotated = inMemorySegment(SegmentType.DEFROST);
        annotated.setAccepted(false);
        annotated.setConfirmedBy("jkowalski");
        session.getDetectedSegmentsMap().put(GridPosition.TOP_FRONT_LEFT, List.of(annotated));

        var result = service.detectForSession(session);

        // Detekcja NIE została uruchomiona ponownie — adnotacje zachowane
        verify(regimeDetectionService, never()).detect(any(), any());
        assertThat(result.get(GridPosition.TOP_FRONT_LEFT).get(0).getConfirmedBy())
                .isEqualTo("jkowalski");
        assertThat(result.get(GridPosition.TOP_FRONT_LEFT).get(0).isAccepted()).isFalse();
    }

    @Test
    @DisplayName("TC-ANN-007: detectForSession uruchamia detekcję dla pustej mapy segmentów")
    void tc_ann_007_detectRunsWhenMapEmpty() {
        RevalidationSession session = new RevalidationSession();
        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder().id(1L).build();
        RevalidationSession.PositionData data = RevalidationSession.PositionData.builder()
                .series(series)
                .build();
        session.getAssignedPositions().put(GridPosition.TOP_FRONT_LEFT, data);

        MeasurementSegment detected = inMemorySegment(SegmentType.STEADY_STATE);
        when(regimeDetectionService.detect(any(), any()))
                .thenReturn(DetectionResult.of(List.of(detected)));

        var result = service.detectForSession(session);

        assertThat(result).containsKey(GridPosition.TOP_FRONT_LEFT);
        assertThat(result.get(GridPosition.TOP_FRONT_LEFT)).containsExactly(detected);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "pw"));
    }

    private MeasurementSegment inMemorySegment(SegmentType type) {
        return MeasurementSegment.builder()
                .type(type)
                .fromTimestamp(T0)
                .toTimestamp(T0.plusMinutes(30))
                .confidence(0.9)
                .source(DetectionSource.ALGORITHM)
                .build();
    }
}
