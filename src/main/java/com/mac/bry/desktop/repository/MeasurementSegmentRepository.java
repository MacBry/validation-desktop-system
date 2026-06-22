package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repozytorium segmentów reżimów pracy.
 */
@Repository
public interface MeasurementSegmentRepository extends JpaRepository<MeasurementSegment, Long> {

    /**
     * Wszystkie segmenty dla danej serii, posortowane chronologicznie.
     */
    List<MeasurementSegment> findBySeriesIdOrderByFromTimestampAsc(Long seriesId);

    /**
     * Zaakceptowane segmenty danego typu dla serii — używane przez RegimeAwareStatsService.
     */
    @Query("""
            SELECT s FROM MeasurementSegment s
            WHERE s.series.id = :seriesId
              AND s.type = :type
              AND s.accepted = true
            ORDER BY s.fromTimestamp ASC
            """)
    List<MeasurementSegment> findAcceptedBySeriesIdAndType(
            @Param("seriesId") Long seriesId,
            @Param("type") SegmentType type);

    /**
     * Usuwa wszystkie algorytmicznie wykryte segmenty dla serii
     * (przed ponowną detekcją — nie usuwa adnotacji OPERATOR).
     */
    @Query("""
            DELETE FROM MeasurementSegment s
            WHERE s.series.id = :seriesId
              AND s.source = com.mac.bry.desktop.model.regime.DetectionSource.ALGORITHM
            """)
    void deleteAlgorithmicBySeriesId(@Param("seriesId") Long seriesId);

    /**
     * Sprawdza czy seria ma co najmniej jeden zaakceptowany segment STEADY_STATE.
     */
    @Query("""
            SELECT COUNT(s) > 0 FROM MeasurementSegment s
            WHERE s.series.id = :seriesId
              AND s.type = com.mac.bry.desktop.model.regime.SegmentType.STEADY_STATE
              AND s.accepted = true
            """)
    boolean hasSteadyState(@Param("seriesId") Long seriesId);
}
