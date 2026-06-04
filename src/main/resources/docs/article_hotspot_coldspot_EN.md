🌡️ Hotspot and Coldspot in Temperature Mapping: Five Methods, Five Different Verdicts. Which Sensor Do You Point At for the Auditor?

I'm facing another design decision in my validation system, and this one is more subtle than it first appears.

The starting point is clear. The regulatory documents — USP <1079.4> (official since May 1, 2024) and WHO TRS 961, Annex 9, Supplement 8 (2015) — require identification of hotspot and coldspot, and a documented rationale for the placement of continuous monitoring sensors in the validation protocol. WHO puts it plainly: mapping is used to identify zones where remedial action may be needed, for example by altering air distribution to eliminate hot and cold spots.

What is **not** in those documents — a specific statistical algorithm for finding those points. The choice of method is a design decision the company has to make and justify in the protocol. And it's that **choice**, not the measurements themselves, that decides which sensor you ultimately point at.

Let me show this on a concrete data set from my simulation. A pharmaceutical refrigerator 2–8°C, 9 sensors: 4 on the top shelf, 4 on the bottom, 1 in the center. A 24-hour mapping session, sampling every 5 minutes — consistent with WHO TRS 961, which recommends 24–72 hours for cold rooms.

The chamber has a realistic vertical gradient of ~3°C (top warmer), measurement noise ~0.15°C, and four "real-life" events during the mapping:

▪️ T2 (top NE) — brief door opening at hour 12, spike to 9.1°C for 15 minutes
▪️ T3 (top SE) — slow drift +0.6°C across 24 hours (degrading thermal contact)
▪️ T4 (top SW) — faulty defrost cycle, 90 minutes at 8.3°C
▪️ B3 (bottom SE) — one-off artifact -0.8°C (brief contact with a cold wall during mounting)

Results for the 9 sensors (°C):

```
Sensor      AbsMax  AbsMin  Mean   P99    MKT   TOL>8°C  TOL<2°C
T1_top_NW    7.08   6.01   6.50   6.85   6.50    0       0
T2_top_NE    9.10   5.03   5.43   6.11   5.44    8       0   ← door spike
T3_top_SE    7.13   5.94   6.51   7.03   6.51    0       0   ← drift
T4_top_SW    8.33   5.37   5.97   8.29   5.99   15.4     0   ← limit breach
C_center     5.39   4.61   5.01   5.35   5.01    0       0
B1_bot_NW    3.98   3.06   3.52   3.88   3.52    0       0
B2_bot_NE    3.67   2.76   3.20   3.53   3.20    0       0   ← steadily coldest
B3_bot_SE    4.31  -0.80   3.88   4.21   3.89    0      14   ← mounting artifact
B4_bot_SW    4.07   3.20   3.61   4.00   3.61    0       0
```

(TOL = Time-Over/Under-Limit, °C·min above 8°C or below 2°C)

Watch what happens as you change the method:

🔴 HOTSPOT — five methods, THREE different verdicts
▪️ Absolute Maximum → T2 (9.10°C) — catches the door-opening spike
▪️ Arithmetic Mean → T3 (6.51°C) — catches the drifting sensor
▪️ MKT (Arrhenius) → T3 (6.51°C) — same as mean, no extremes to weight
▪️ Percentile 99 → T4 (8.29°C) — catches the 90-minute limit breach
▪️ Time-Over-Limit → T4 (15.4 °C·min) — same conclusion, risk-quantified

🔵 COLDSPOT — four methods, DWA different verdicts
▪️ Absolute Minimum → B3 (-0.80°C) — catches the mounting artifact
▪️ Mean / Percentile 1 → B2 (3.20°C / 2.87°C) — catches the steadily coldest sensor
▪️ Time-Under-Limit → B3 (14 °C·min) — quantifies the breach

These are **different physical sensors**. Placing continuous monitoring on T2 vs T3 vs T4 means three different validation decisions. Each has a rationale. Each can be defended to an auditor. **But only one ends up in the protocol.**

⚙️ What each method says (and hides)

❌ Absolute Maximum (worst-case)
Catches everything, including artifacts. T2 became the "hotspot" purely because someone opened the door for 15 minutes. Consequence: you'd place continuous monitoring at the location of an *incident*, not a sustained risk zone.

❌ Arithmetic Mean
Hides dynamics. A sensor steady at 5.0°C and one oscillating between 1°C and 9°C have the same mean. **This is not a method listed in the regulatory documents** — I include it as one of the candidates worth discussing.

⚠️ MKT (Arrhenius, ΔH = 83 144 J/mol, R = 8.314 J/(mol·K))
A non-linear average weighted by chemical degradation kinetics. For hotspot in thermolabile drugs, it has solid chemical grounding. But it mathematically dampens cold excursions — **do not use it for coldspot**.

**Important regulatory note**: USP <1079.2> (official since December 2020) limits the data window for MKT calculation. For CRT (Controlled Room Temperature, 20–25°C) it's 30 days maximum. **For CCT (Controlled Cold Temperature, 2–8°C) it's 24 hours**. The most common misuse of MKT has been calculating from 52 weeks of data, which mathematically dilutes the result and can hide actual thermal abuse. A 24-hour mapping session for a CCT refrigerator is exactly aligned with this window.

⚠️ Percentiles (P99 / P01)
**Non-standard approach drawn from general statistical practice** — I haven't found a reference to percentiles in USP, WHO, or ISPE documents as a canonical mapping method. Strength: rejects the top 1% as noise. Weakness: a 10-minute power outage during 24h of mapping is 0.69% of the data — falls inside the rejected tail. The longer the mapping window, the more real failures can be "statistically buried." The threshold choice (99? 95?) is arbitrary and needs justification in the protocol.

⚠️ Time-Over-Limit (cumulative excursion index)
**Own proposal**, inspired by the concept of cumulative thermal exposure that underpins MKT. Measures the integral of depth-times-duration past the validation limit. Closest to actual product risk. Problem: if no zone crossed the limit, all sensors score zero — the method cannot identify a hotspot in a stable chamber. Works as a *companion*, not as a sole criterion.

🚫 What the classic per-sensor analysis misses

All five methods treat each sensor in isolation. But the sensors sit in a 3D grid inside the chamber. What if the actual hotspot lies **between** sensors, 15 cm from T2?

Professional mapping tools (Kaye, Vaisala viewLinc, Ellab) use **spatial interpolation** — typically kriging or radial basis functions (RBF) — to estimate the temperature field beyond the measurement points. The hotspot is then not a sensor, but an interpolated location in the chamber space.

This goes beyond what regulatory documents require — **I have not found a formal requirement for spatial interpolation in USP <1079.4>, WHO TRS 961, or the ISPE Good Practice Guide**. It's a "nice to have" rooted in sound engineering practice.

Validation consequence is real, though: if you place monitoring at the sensor "from mapping" while the true hotspot is between sensors, you're monitoring *almost* the right thing. An experienced auditor will notice.

🎯 Where I currently lean (not yet locked in)

A three-layer hybrid, with each layer separately justified in the protocol:

▪️ **Coldspot**: AbsMin + TOL<L as a check. Water crystallization has immediate effect — a sub-zero drop, however brief, must be caught. But the final monitoring location should account for event frequency too (artifact vs systematically cold zone).

▪️ **Hotspot**: MKT + P99 + TOL>U taken together, not as competing verdicts. If all three point at the same sensor — strong signal. If they disagree — the protocol must explain why this one and not another.

▪️ **Spatial layer (experimental)**: RBF interpolation across the 9-sensor grid, reported as a supplementary result, clearly labeled "exceeds regulatory requirements."

❓ A question to anyone who has written mapping protocols for a real audit

Do your protocols use one "sacred" algorithm for hotspot identification, or a hybrid with per-decision justification? And have you ever seen an auditor ask about spatial interpolation, or is that still purely internal best practice?

I'd genuinely like to hear. 💬

📚 References
• USP <1079.4> Temperature Mapping for the Qualification of Storage Areas (USP-NF, official since 2024-05-01)
• USP <1079.2> Mean Kinetic Temperature in the Evaluation of Temperature Excursions (USP-NF, official since 2020-12-01)
• USP <1079.3> Monitoring Devices — Time, Temperature, and Humidity (USP-NF)
• USP <1079> Risks and Mitigation Strategies for the Storage and Transportation of Finished Drug Products (USP-NF)
• WHO Technical Report Series 961, Annex 9, Supplement 8: Temperature mapping of storage areas (May 2015)
• ISPE Good Practice Guide: Controlled Temperature Chambers Version 2.0 (2021)
• ICH Q1A(R2) Stability Testing of New Drug Substances and Products
• PIC/S Guide to Good Distribution Practice PE 011-1 (2014)

#GMP #GxP #FDA #ColdChain #Validation #Pharma #DataAnalysis #ThermalMapping
