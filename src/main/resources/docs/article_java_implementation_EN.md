# Implementing Hybrid Hotspot/Coldspot Detection in Spring Boot — Java 21, Sealed Strategies, and Two Bugs Worth Sharing

*A walkthrough of the detection layer of my validation-system, with full source and the two bugs I had to fix before the code was honest about its own limitations.*

---

In the previous article I argued that hotspot/coldspot detection in GMP cold-chain mapping is not a single algorithm — it's a methodological choice that determines the result. Five methods gave three different verdicts on the same data set. The conclusion was that the system has to evaluate multiple methods and produce a consensus that an auditor can defend.

This article is about turning that argument into code. Specifically: a Spring Boot module that wires five strategies, votes across them, and refuses to lie about degenerate cases.

I'll show the design choices that matter, the bugs I hit, and the full source is below. Java 21, no external dependencies beyond the JDK.

---

## The shape of the problem

Each mapping run produces N sensors (typically 9 to 27) of pre-aggregated statistics. The detection layer has to:

1. Apply each registered strategy independently.
2. Return per-strategy verdicts for the audit trail — every method considered, not just the winner.
3. Aggregate into a consensus that has well-defined behaviour for ties and for "no detection possible".
4. Be impossible to extend silently — every method that can appear in a protocol must go through code review.

That last point ruled out a few obvious designs. Reading a method name from a YAML config and reflecting it into a class? Fast to ship, impossible to audit. A Map of String → Function? Fine for a demo, no compile-time guarantee that registered strategies cover both hotspot and coldspot.

---

## The data model — immutable, pre-aggregated

The detection layer never sees raw measurement points. It sees per-sensor aggregates:

```java
public record SensorStats(
        String sensorId,
        double absMax,
        double absMin,
        double mean,
        double p99,
        double p01,
        double mkt,
        double tolHi,   // Time-Over-Limit (°C·min above upper bound)
        double tolLo    // Time-Under-Limit (°C·min below lower bound)
) {
    public double get(StatField field) {
        return switch (field) {
            case ABS_MAX -> absMax;
            case ABS_MIN -> absMin;
            case MEAN    -> mean;
            case P99     -> p99;
            case P01     -> p01;
            case MKT     -> mkt;
            case TOL_HI  -> tolHi;
            case TOL_LO  -> tolLo;
        };
    }

    public enum StatField {
        ABS_MAX, ABS_MIN, MEAN, P99, P01, MKT, TOL_HI, TOL_LO
    }
}
```

Two things here are deliberate. The `get(StatField)` dispatch is a switch expression, not a Map — the compiler enforces that every field has a branch, so adding a new field is a compile error until every consumer is updated. And the whole record is immutable, which matters when this object will be JSON-serialized into an audit trail and signed.

---

## The strategy interface — sealed

The contract is small. Each strategy declares which field it reads, whether it's a hotspot or coldspot method, and produces a `Verdict`:

```java
public sealed interface ExtremeDetectionStrategy
        permits AbsMaxStrategy, AbsMinStrategy, MeanStrategy,
                MktStrategy, PercentileStrategy, TimeOverLimitStrategy {

    String methodCode();           // stable machine ID, e.g. "ABS_MAX_HOTSPOT"
    String displayName();          // human label for the protocol
    boolean isHotspot();
    SensorStats.StatField field();

    default Optional<Verdict> apply(List<SensorStats> stats) { ... }
    default boolean isDegenerate(SensorStats s) { return false; }

    record Verdict(
            String methodCode,
            String methodName,
            String winnerSensorId,
            double value,
            boolean isHotspot
    ) {}
}
```

The `sealed` keyword is the part doing the regulatory work. Anyone adding a new detection method has to:

1. Create the class.
2. Update the `permits` clause of `ExtremeDetectionStrategy`.

Both happen in the same pull request. The diff shows up in code review. The compiler refuses to accept anonymous strategy classes from outside the package. This is exactly the design property GxP code needs — no mechanism for unauthorised methods to slip into production.

> [!TIP]
> If you are building the project in an environment with an older language level (e.g. JDK 16 or lower) that does not yet fully support sealed classes, you can simply remove the `sealed` keyword and the `permits` clause. The code will retain all functionality, and the interface will become a standard contract.


Each concrete strategy is then a couple of lines:

```java
public final class AbsMaxStrategy implements ExtremeDetectionStrategy {
    @Override public String methodCode()           { return "ABS_MAX_HOTSPOT"; }
    @Override public String displayName()          { return "Absolute Maximum (worst-case)"; }
    @Override public boolean isHotspot()           { return true; }
    @Override public SensorStats.StatField field() { return SensorStats.StatField.ABS_MAX; }
}
```

Five lines per method. Everything else lives in the default `apply()`.

---

## Bug #1 — the all-zeros problem

Here's the original `apply()` I wrote first:

```java
default Verdict apply(List<SensorStats> stats) {
    var f = field();
    var winner = isHotspot()
            ? stats.stream().max((a, b) -> Double.compare(a.get(f), b.get(f)))
            : stats.stream().min((a, b) -> Double.compare(a.get(f), b.get(f)));
    return new Verdict(methodCode(), displayName(),
            winner.get().sensorId(), winner.get().get(f), isHotspot());
}
```

Runs on the 9-sensor demo. Time-Under-Limit returns `T1_top_NW` with value `0.00`.

T1 is on the top shelf — it never went near the lower bound. Neither did any other sensor except B3 (which is the actual coldspot). What happened is that every non-B3 sensor has `tolLo = 0.0`, and `Stream.min` on a sea of zeros just returns the first one encountered. The Verdict is technically correct: T1 *does* have the minimum `tolLo`. It's tied for minimum with seven other sensors at exactly zero.

That Verdict, if shipped, would be wrong in the worst possible way. An auditor reading the protocol would see "TUL detection: T1_top_NW" and conclude that the top shelf is the coldest part of the chamber. Both the value (0.00) and the absurdity of the result would be invisible to anyone running an automated check.

The fix is to make degenerate cases explicit. Strategies override `isDegenerate(SensorStats s)` to declare which sensors carry no signal under their method, and `apply()` returns `Optional.empty()` when no signal remains:

```java
default Optional<Verdict> apply(List<SensorStats> stats) {
    var meaningful = stats.stream()
            .filter(s -> !isDegenerate(s))
            .toList();

    if (meaningful.isEmpty()) {
        return Optional.empty();   // explicit "no detection"
    }
    // ... pick winner from meaningful
}
```

For Time-Over/Under-Limit, "no signal" means the sensor stayed within bounds:

```java
@Override
public boolean isDegenerate(SensorStats s) {
    return field() == SensorStats.StatField.TOL_HI ? s.tolHi() == 0.0
                                                   : s.tolLo() == 0.0;
}
```

Now the demo is honest: when no sensor exceeds its limit, TOL contributes no vote at all rather than picking an arbitrary winner.

This is a small change at the API level (return type goes from `Verdict` to `Optional<Verdict>`), but it propagates everywhere downstream. The consensus service has to handle empty verdicts. The audit trail has to record "this method was tried and returned no detection" distinctly from "this method was not tried." That's the right tradeoff — the API now models reality.

---

## Bug #2 — comparing °C with °C·min

The consensus service aggregates per-method verdicts by majority vote. With five hotspot methods I expected occasional ties — and tie-breaking is exactly the kind of thing that's easy to get wrong.

My first attempt was the seductive one-liner:

```java
// Tie-break by highest extreme value
.thenComparing(e -> extremeValueFor(e.getKey(), verdicts, isHotspot))
```

It compiles. It runs. On the demo:

- Mean and MKT vote for T3 (value: 6.51 °C)
- P99 and TOL vote for T4 (P99: 8.29 °C, **TOL: 15.40 °C·min**)
- AbsMax votes for T2

Tie: T3 and T4, both at 2 votes. The "highest value" tie-break picks T4 because 15.40 > 6.51. But the 15.40 is *°C·min*, the integral of excursion over time. The 6.51 is *°C*. You can't order them numerically — they're not in the same space.

T4 happens to be the right answer in this case (it had a 90-minute excursion above 8°C, which is more concerning than T3's stable 6.51°C). But the tie-break got there by a unit-mismatch comparison. Change the mapping length from 24h to 72h and the integral grows; change it to 1h and it shrinks; the verdict could flip without any of the underlying physics changing.

The fix is to tie-break by **methodological priority**, not raw value:

```java
private static final List<String> METHOD_PRIORITY = List.of(
        "TOL_HI_HOTSPOT", "TOL_LO_COLDSPOT",
        "ABS_MAX_HOTSPOT", "ABS_MIN_COLDSPOT",
        "MKT_HOTSPOT",
        "P99_HOTSPOT", "P01_COLDSPOT",
        "MEAN_HOTSPOT", "MEAN_COLDSPOT"
);
```

Order: methods directly tied to regulatory risk (TOL, absolute extremes) outrank statistical summaries. The reasoning is in the comment, and the ordering itself lives in a single visible constant — not buried in a lambda. An auditor asking "how did you break the tie?" gets a one-line answer that points to a list.

```java
private static String tieBreakByMethodPriority(
        List<String> candidates,
        List<Verdict> verdicts) {

    for (String method : METHOD_PRIORITY) {
        var pick = verdicts.stream()
                .filter(v -> v.methodCode().equals(method))
                .filter(v -> candidates.contains(v.winnerSensorId()))
                .findFirst();
        if (pick.isPresent()) return pick.get().winnerSensorId();
    }
    return candidates.get(0);
}
```

The output is identical for the demo (T4 wins) — but for the right reason now.

---

## The consensus report

The aggregation produces a record that goes straight into the audit trail:

```java
public record ConsensusReport(
        boolean isHotspot,
        String consensusSensorId,
        double consensusStrength,         // fraction agreeing (0.0–1.0)
        Map<String, Integer> votesByCandidate,
        List<Verdict> allVerdicts          // every method's verdict
) {
    public boolean hasDetection() { return consensusSensorId != null; }
    public boolean isUnanimous()  { return consensusStrength == 1.0; }
    public boolean isWeak()       { return consensusStrength < 0.5; }
}
```

`isWeak()` is the bit the protocol cares about. A weak consensus (< 50% agreement) doesn't decide anything — it escalates to QA review. The system says "here are five methods, they pointed at three different sensors, you need to choose and justify."

Running the demo:

```
HOTSPOT — per-method verdicts:
  Absolute Maximum (worst-case)         -> T2_top_NE  (9.10)
  Mean (steady-state)                   -> T3_top_SE  (6.51)
  MKT (Arrhenius, ΔH=83144 J/mol)       -> T3_top_SE  (6.51)
  Percentile 99                         -> T4_top_SW  (8.29)
  Time-Over-Limit (>U)                  -> T4_top_SW  (15.40)

  CONSENSUS: T4_top_SW (strength: 40%)
  Vote distribution: {T3=2, T4=2, T2=1}
  ⚠ WEAK CONSENSUS — QA review required.
```

40% strength flags this as a case that needs human judgement, with the full vote distribution preserved for the QA manager to reason over.

---

## What the article series leaves out (and why)

The spatial interpolation layer — RBF over the 9-sensor grid — is the next sprint. I'm prototyping it as a Python microservice (SciPy `RBFInterpolator` is one line; the Java equivalents in Apache Commons Math don't handle irregular 3D grids well). The Spring Boot side will own orchestration, audit trail, and persistence; the Python side does the pure math. A cleaner separation than trying to bring numerical methods into the Java codebase for a 9-point grid.

The full source for the detection layer (everything in this article, plus the demo runner) is in my GitHub repo. The codebase compiles on JDK 21 with no external dependencies — paste the files into a `src/main/java` tree and it runs.

→ [github.com/MacBry/validation-system](https://github.com/MacBry/validation-system)

---

## What I want to know

If you've built similar detection layers in regulated software — what's your tie-break policy when multiple methods disagree? And: do you treat "no detection" as a first-class outcome, or do you let the API return a meaningless winner?

The Optional/empty pattern feels right to me but it's invasive across the codebase. Curious if anyone has a lighter-weight design that still keeps the "all zeros" problem out of the audit trail.

#Java #SpringBoot #SoftwareArchitecture #GxP #ValidationSystem #DesignPatterns #JDK21
