package com.mac.bry.desktop.service;

import com.mac.bry.desktop.dto.stats.ChamberSessionSnapshot;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.repository.ThermoMeasurementSeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testy jednostkowe logiki porównań między sesjami rewalidacji.
 * Repozytorium jest mockowane — sprawdzamy grupowanie po {@code revalidationGroupId},
 * agregaty per sesja, delty vs sesja poprzednia oraz wybór baseline'u.
 */
class SessionComparisonServiceTest {

    private static final Long CHAMBER_ID = 1L;

    private ThermoMeasurementSeriesRepository repository;
    private SessionComparisonService service;

    @BeforeEach
    void setUp() {
        repository = mock(ThermoMeasurementSeriesRepository.class);
        service = new SessionComparisonService(repository);
    }

    /** Buduje serię tylko z polami, których używa serwis (reszta nieistotna dla logiki agregacji). */
    private ThermoMeasurementSeries series(String groupId, GridPosition position,
                                           Double avg, Double std, Integer spikes,
                                           LocalDateTime importedAt) {
        return ThermoMeasurementSeries.builder()
                .revalidationGroupId(groupId)
                .gridPosition(position)
                .avgTemperature(avg)
                .stdDeviation(std)
                .spikeCount(spikes)
                .importedAt(importedAt)
                .build();
    }

    @Nested
    @DisplayName("getSessionHistory")
    class SessionHistory {

        @Test
        @DisplayName("grupuje serie po revalidationGroupId i sortuje sesje chronologicznie")
        void groupsAndSortsChronologically() {
            LocalDateTime older = LocalDateTime.of(2026, 1, 10, 8, 0);
            LocalDateTime newer = LocalDateTime.of(2026, 4, 10, 8, 0);

            when(repository.findByCoolingChamberId(CHAMBER_ID)).thenReturn(List.of(
                    // Sesja nowsza (G2) — celowo podana jako pierwsza, by wymusić sortowanie
                    series("G2", GridPosition.TOP_FRONT_RIGHT, 5.1, 0.18, 2, newer),
                    series("G2", GridPosition.BOTTOM_FRONT_RIGHT, 6.2, 0.30, 14, newer),
                    // Sesja starsza (G1)
                    series("G1", GridPosition.TOP_FRONT_RIGHT, 5.0, 0.15, 0, older),
                    series("G1", GridPosition.BOTTOM_FRONT_RIGHT, 5.8, 0.24, 9, older)
            ));

            List<ChamberSessionSnapshot> history = service.getSessionHistory(CHAMBER_ID);

            assertThat(history).extracting(ChamberSessionSnapshot::getGroupId)
                    .containsExactly("G1", "G2");
            assertThat(history).extracting(ChamberSessionSnapshot::getSessionDate)
                    .containsExactly(older, newer);
        }

        @Test
        @DisplayName("liczy agregaty sesji: średnią, ΔT przestrzenne, max std, hot/coldspot, sumę szpilek")
        void computesAggregates() {
            LocalDateTime date = LocalDateTime.of(2026, 1, 10, 8, 0);
            when(repository.findByCoolingChamberId(CHAMBER_ID)).thenReturn(List.of(
                    series("G1", GridPosition.TOP_FRONT_RIGHT, 5.0, 0.15, 0, date),
                    series("G1", GridPosition.BOTTOM_FRONT_RIGHT, 5.8, 0.24, 9, date)
            ));

            ChamberSessionSnapshot s = service.getSessionHistory(CHAMBER_ID).get(0);

            assertThat(s.getSeriesCount()).isEqualTo(2);
            assertThat(s.getMeanTemperature()).isCloseTo(5.4, within(1e-9));
            assertThat(s.getSpatialDeltaT()).isCloseTo(0.8, within(1e-9));
            assertThat(s.getMaxStdDeviation()).isCloseTo(0.24, within(1e-9));
            assertThat(s.getHotspotPosition()).isEqualTo(GridPosition.BOTTOM_FRONT_RIGHT);
            assertThat(s.getColdspotPosition()).isEqualTo(GridPosition.TOP_FRONT_RIGHT);
            assertThat(s.getTotalSpikeCount()).isEqualTo(9);
        }

        @Test
        @DisplayName("wylicza delty vs poprzednia sesja (null dla pierwszej)")
        void computesDeltasVsPrevious() {
            LocalDateTime older = LocalDateTime.of(2026, 1, 10, 8, 0);
            LocalDateTime newer = LocalDateTime.of(2026, 4, 10, 8, 0);
            when(repository.findByCoolingChamberId(CHAMBER_ID)).thenReturn(List.of(
                    series("G1", GridPosition.TOP_FRONT_RIGHT, 5.0, 0.15, 0, older),
                    series("G1", GridPosition.BOTTOM_FRONT_RIGHT, 5.8, 0.24, 9, older),
                    series("G2", GridPosition.TOP_FRONT_RIGHT, 5.1, 0.18, 2, newer),
                    series("G2", GridPosition.BOTTOM_FRONT_RIGHT, 6.2, 0.30, 14, newer)
            ));

            List<ChamberSessionSnapshot> history = service.getSessionHistory(CHAMBER_ID);

            // Pierwsza sesja — brak referencji, delty null
            assertThat(history.get(0).getDeltaSpatialDeltaT()).isNull();
            assertThat(history.get(0).getDeltaMaxStdDeviation()).isNull();

            // Druga sesja — pogorszenie jednorodności (0.8 → 1.1) i stabilności (0.24 → 0.30)
            assertThat(history.get(1).getDeltaSpatialDeltaT()).isCloseTo(0.3, within(1e-9));
            assertThat(history.get(1).getDeltaMaxStdDeviation()).isCloseTo(0.06, within(1e-9));
        }

        @Test
        @DisplayName("pomija szybkie odczyty USB bez revalidationGroupId")
        void ignoresSeriesWithoutGroup() {
            LocalDateTime date = LocalDateTime.of(2026, 1, 10, 8, 0);
            when(repository.findByCoolingChamberId(CHAMBER_ID)).thenReturn(List.of(
                    series("G1", GridPosition.TOP_FRONT_RIGHT, 5.0, 0.15, 0, date),
                    series(null, GridPosition.TOP_FRONT_RIGHT, 4.9, 0.20, 1, date),
                    series("  ", GridPosition.BOTTOM_FRONT_RIGHT, 6.0, 0.22, 3, date)
            ));

            List<ChamberSessionSnapshot> history = service.getSessionHistory(CHAMBER_ID);

            assertThat(history).hasSize(1);
            assertThat(history.get(0).getGroupId()).isEqualTo("G1");
            assertThat(history.get(0).getSeriesCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("zwraca pustą listę, gdy komora nie ma serii")
        void emptyWhenNoSeries() {
            when(repository.findByCoolingChamberId(CHAMBER_ID)).thenReturn(List.of());
            assertThat(service.getSessionHistory(CHAMBER_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findBaseline")
    class Baseline {

        @Test
        @DisplayName("zwraca najnowszą WCZEŚNIEJSZĄ sesję dla pozycji, wykluczając bieżącą grupę")
        void returnsLatestEarlierExcludingCurrent() {
            LocalDateTime older = LocalDateTime.of(2026, 1, 10, 8, 0);
            LocalDateTime newer = LocalDateTime.of(2026, 4, 10, 8, 0);
            when(repository.findByCoolingChamberId(CHAMBER_ID)).thenReturn(List.of(
                    series("G1", GridPosition.BOTTOM_FRONT_RIGHT, 5.8, 0.24, 9, older),
                    series("G2", GridPosition.BOTTOM_FRONT_RIGHT, 6.2, 0.30, 14, newer)
            ));

            Optional<SessionComparisonService.PositionBaseline> baseline =
                    service.findBaseline(CHAMBER_ID, GridPosition.BOTTOM_FRONT_RIGHT, "G2");

            assertThat(baseline).isPresent();
            assertThat(baseline.get().groupId()).isEqualTo("G1");
            assertThat(baseline.get().avgTemperature()).isEqualTo(5.8);
            assertThat(baseline.get().stdDeviation()).isEqualTo(0.24);
        }

        @Test
        @DisplayName("gdy currentGroupId=null, bierze najnowszą dostępną sesję")
        void takesLatestWhenCurrentNull() {
            LocalDateTime older = LocalDateTime.of(2026, 1, 10, 8, 0);
            LocalDateTime newer = LocalDateTime.of(2026, 4, 10, 8, 0);
            when(repository.findByCoolingChamberId(CHAMBER_ID)).thenReturn(List.of(
                    series("G1", GridPosition.BOTTOM_FRONT_RIGHT, 5.8, 0.24, 9, older),
                    series("G2", GridPosition.BOTTOM_FRONT_RIGHT, 6.2, 0.30, 14, newer)
            ));

            Optional<SessionComparisonService.PositionBaseline> baseline =
                    service.findBaseline(CHAMBER_ID, GridPosition.BOTTOM_FRONT_RIGHT, null);

            assertThat(baseline).isPresent();
            assertThat(baseline.get().groupId()).isEqualTo("G2");
            assertThat(baseline.get().avgTemperature()).isEqualTo(6.2);
        }

        @Test
        @DisplayName("filtruje po pozycji — inna pozycja nie stanowi baseline'u")
        void filtersByPosition() {
            LocalDateTime date = LocalDateTime.of(2026, 1, 10, 8, 0);
            when(repository.findByCoolingChamberId(CHAMBER_ID)).thenReturn(List.of(
                    series("G1", GridPosition.TOP_FRONT_RIGHT, 5.0, 0.15, 0, date)
            ));

            Optional<SessionComparisonService.PositionBaseline> baseline =
                    service.findBaseline(CHAMBER_ID, GridPosition.BOTTOM_FRONT_RIGHT, "G2");

            assertThat(baseline).isEmpty();
        }

        @Test
        @DisplayName("pomija serie bez avg/std przy szukaniu baseline'u")
        void skipsSeriesWithoutStats() {
            LocalDateTime date = LocalDateTime.of(2026, 1, 10, 8, 0);
            when(repository.findByCoolingChamberId(CHAMBER_ID)).thenReturn(List.of(
                    series("G1", GridPosition.BOTTOM_FRONT_RIGHT, null, null, null, date)
            ));

            Optional<SessionComparisonService.PositionBaseline> baseline =
                    service.findBaseline(CHAMBER_ID, GridPosition.BOTTOM_FRONT_RIGHT, "G2");

            assertThat(baseline).isEmpty();
        }
    }
}
