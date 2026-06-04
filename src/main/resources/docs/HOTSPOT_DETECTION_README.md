# Hotspot/Coldspot Detection Module

Hybrid detection of thermal extremes (hotspot/coldspot) from sensor mapping
statistics. Used during refrigeration equipment validation (IQ/OQ/PQ) to
identify the worst-case storage locations that must be covered by continuous
monitoring sensors.

## Design

The module is split into three conceptual layers:

1. **Per-sensor strategies** — each detection method (Absolute Max, Mean, MKT,
   Percentile, Time-Over-Limit) implemented as a separate `sealed interface`
   strategy. The `sealed` keyword enforces compile-time review of any new
   method added to the validation protocol.

2. **Consensus aggregation** — `ConsensusDetectionService` runs all
   registered strategies, votes across their verdicts, and resolves ties by
   methodological priority (NOT by raw value — see Bug #2 notes below).

3. **Spatial interpolation** *(planned, separate sprint)* — RBF interpolation
   over the 3D sensor grid, implemented as a Python microservice.

## Files

| File | Purpose |
|------|---------|
| `SensorStats.java` | Immutable DTO with pre-computed per-sensor statistics |
| `ExtremeDetectionStrategy.java` | `sealed interface` — root of the strategy hierarchy |
| `AbsMaxStrategy.java` | Picks sensor with the highest absolute maximum |
| `AbsMinStrategy.java` | Picks sensor with the lowest absolute minimum |
| `MeanStrategy.java` | Picks sensor with the highest/lowest arithmetic mean |
| `MktStrategy.java` | Picks sensor with the highest Mean Kinetic Temperature (Arrhenius) |
| `PercentileStrategy.java` | Picks sensor with the highest P99 (hotspot) or lowest P01 (coldspot) |
| `TimeOverLimitStrategy.java` | Picks sensor with the largest cumulative excursion past validation limits |
| `ConsensusDetectionService.java` | Aggregates strategy verdicts into a consensus report |
| `HotspotDemo.java` | Runnable demo with the 9-sensor mapping data from the article series |

## Known design decisions

### Why `sealed interface`?

In a GxP context, any new detection method that can appear in a validation
protocol must go through code review. A `sealed` interface enforces this at
compile time: adding a new strategy requires both creating the class *and*
updating the `permits` clause — both visible in the diff.

### Why `Optional<Verdict>` instead of `Verdict`?

Detection methods can fail to produce a meaningful result. Time-Over-Limit
on a chamber that stayed within bounds returns all zeros — picking the
"first" sensor with tolHi=0 would be arbitrary and misleading in the audit
trail. Strategies declare degenerate inputs via `isDegenerate(SensorStats)`,
and `apply()` returns `Optional.empty()` when no signal remains.

### Tie-break policy

When multiple sensors tie for most votes, the consensus service breaks the
tie by **methodological priority**, not raw value. Comparing °C (from Mean,
MKT, Percentile) with °C·min (from Time-Over-Limit) is a unit-mismatch bug;
the priority order is defined in `METHOD_PRIORITY` and ranks methods tied
to regulatory risk (TOL, absolute extremes) above statistical summaries.

## Running the demo

No external dependencies — JDK 21 only.

```bash
javac -d out src/main/java/com/validation/hotspot/*.java
java -cp out com.validation.hotspot.HotspotDemo
```

Expected output: per-method verdicts for the 9-sensor mapping data set,
plus consensus reports for hotspot and coldspot. The hotspot case
demonstrates a weak consensus (40% strength) where 3 methods point at 3
different sensors — flagging the case for QA review.

## References

- Article: *Hotspot i Coldspot w mapowaniu temperatury: pięć metod, pięć
  różnych werdyktów* (statistical/BA analysis of detection methods)
- Article: *Implementing Hybrid Hotspot/Coldspot Detection in Spring Boot —
  Java 21, Sealed Strategies, and Two Bugs Worth Sharing* (this module)
- WHO Technical Report Series 961 — guidance on temperature mapping
- ICH Q1A(R2) — Mean Kinetic Temperature definition (ΔH = 83 144 J/mol)
