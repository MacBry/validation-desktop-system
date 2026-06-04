# Sources for Hotspot/Coldspot Mapping Article — Verified Guide

> **Principle**: Every statement below is backed by a specific, verified source. Claims that could not be directly verified by regulation are labeled as "own engineering interpretation".

---

## 1. Reference Documents (Confirmed Current)

### 🔴 Primary — Can Be Quoted Directly

| Document | Status | What It Regulates |
|---|---|---|
| **USP <1079.4>** Temperature Mapping for the Qualification of Storage Areas | **Official since May 1, 2024** | Temperature mapping of pharmaceutical storage — the latest official chapter |
| **USP <1079.2>** Mean Kinetic Temperature in the Evaluation of Temperature Excursions | Official since Dec 1, 2020, updated | MKT calculation window limits (30-day CRT cap, 24-hour CCT cap) |
| **USP <1079>** Risks and Mitigation Strategies for Storage and Transportation | Official | General cold-chain risk management framework |
| **USP <1079.3>** Monitoring Devices — Time, Temperature, and Humidity | Official | Calibration and performance requirements for loggers |
| **WHO TRS 961, Annex 9, Supplement 8** (2015) "Temperature mapping of storage areas" | Official | The most widely cited mapping standard in EU/WHO |

### 🟡 Industry-Recognized — Non-Regulatory Best Practices

| Document | What It Regulates |
|---|---|
| **ISPE Good Practice Guide: Controlled Temperature Chambers Version 2.0** (2021) | Best practices for qualification of temperature-controlled chambers |
| **PIC/S Guide to Good Distribution Practice PE 011-1** (June 2014) | GDP standards for medicinal products in the EU |
| **ICH Q1A(R2)** | Stability testing guidelines — the scientific basis for MKT calculations |

### 🟢 Technical Standards

| Document | What It Regulates |
|---|---|
| **FDA 21 CFR 211** | cGMP regulations for finished pharmaceuticals |
| **FDA 21 CFR Part 11** | Electronic records and electronic signatures (audit trail integrity) |

---

## 2. Key Regulatory Findings Verified in Sources

### Finding A: USP <1079.4> Became Official on May 1, 2024

**Source**: Lachman Consultants, "Finally – A USP General Chapter on Temperature Mapping Studies is Official!" (August 2024).

Direct Quote: *"On May 1, 2024, the first version of USP General Chapter <1079.4> on Temperature Mapping for the Qualification of Storage Areas became official"*.

USP <1079.4> is the **fourth part** of the <1079> suite and requires:
- Evaluation of the storage area (dimensions, HVAC layout, seasonal extremes, loading patterns)
- Documented rationale for temperature probe locations with grid layout maps
- Sufficient quantity of calibrated monitoring devices
- A mapping protocol covering typical workflow changes, loading tests, door-open challenges, and power failure tests
- Mitigation strategies for identified hot/cold spots
- A formally approved final mapping report

**What USP <1079.4> does NOT do**: It does not prescribe a fixed re-mapping frequency (e.g. annually or 3-yearly). Instead, it mandates a formal risk assessment to determine the re-qualification period.

---

### Finding B: USP <1079.2> Imposed Strict Data Window Limits on MKT (2020)

**Source**: USP-NF Chapter <1079.2>; ECA Academy; Sensitech Industry Whitepaper.

Direct Quote: *"MKT can be calculated on an ongoing basis or anytime that there has been a temperature excursion using data going back 30 days from (and including) the high excursion temperature"*.

Sensitech whitepaper (industry interpretation): *"For the Storage of CRT products, USP <1079.2> defines that a maximum of 30 days of temperature data may be used for the calculation of MKT"*.

**Critical Detail for the Article**: USP <1079.2> differentiates between two storage categories:
- **CRT (Controlled Room Temperature, 20–25°C)**: MKT uses a maximum of **30 days** of data.
- **CCT (Controlled Cold Temperature, 2–8°C)**: MKT uses a maximum of **24 hours** of data.

**Why this matters**: Since your pharmaceutical refrigerator operates at 2–8°C (CCT), the regulatory window for MKT is **exactly 24 hours**. This directly aligns with the 24-hour mapping session described in your article.

**Misuse Warning** (quoted from USP): *"the most significant misuse has been utilizing 52 weeks of temperature data to calculate MKT. Drug products typically do not spend 52 weeks in a single storage location"*. Calculating MKT over excessively long windows mathematically dilutes spikes, hiding severe temperature abuse.

---

### Finding C: WHO TRS 961, Supplement 8 Mandates Mapping Duration

**Source**: WHO Technical Supplement to TRS 961, Annex 9, Supplement 8 (May 2015).

Interpretation: *"Mapping should be run for a minimum of 7 consecutive days for warehouse and ambient storage areas and for between 24 and 72 hours for freezer rooms and cold rooms"*.

This **justifies the 24-hour mapping window** used in your article for a cold chamber as fully compliant with WHO recommendations.

---

### Finding D: Hot and Cold Spots Must Be Formally Identified

**Source**: WHO TRS 961, Supplement 8.

Quote: *"mapping may also be used to identify zones where remedial action needs to be taken; for example by altering air distribution to eliminate hot and cold spots"*.

**Source 2**: Leading Minds Network Industry Expert FAQ.

Quote: *"At a minimum, sensors should be placed on the most critical points of the mapping (e.g. hot spot and cold spot). Additional spots risk based and/or covering all spaces"*.

This establishes the core GxP requirement: **continuous monitoring probes must be placed at the worst-case locations (hotspot and coldspot) determined during mapping**.

---

### Finding E: Sensor Calibration Rygor

**Source**: WHO Annex 9, Section 2.1.

Quote: *"...all loggers must have a NIST-traceable 3-point calibration completed and valid (within the current year), and have an error of no more than ± 0.5 °C at each calibration point"*.

This calibration rigor explains why we must carefully analyze raw sensor characteristics rather than assuming perfect accuracy.

---

## 3. What is NOT Prescribed in Regulatory Sources (Own Engineering Decisions)

### ❓ No Recommended Single Mathematical Algorithm

None of the major regulatory guidelines prescribe a specific mathematical algorithm for identifying hotspots or coldspots. The choice of method is left to the manufacturer.

USP <1079.4> requires: *"A rationale for temperature monitoring probe placement, taking into consideration any governing laws and procedures"*.

**This reinforces your core article argument**: Because regulations do not prescribe the math, the engineer must choose the strategies, justify them in the protocol, and build a defendable consensus.

### ❓ Spatial Interpolation (RBF, Kriging) is Optional

We found **no regulatory requirement** forcing companies to use 3D spatial interpolation (such as kriging or Radial Basis Functions) to locate spots between physical loggers. It is a feature of premium commercial software (e.g., Kaye, Vaisala, Ellab) but not a compliance mandate.

**Article Status**: Present it as an "advanced engineering practice that exceeds regulatory minimums to ensure better product safety."

### ❓ Time-Over-Limit (TOL) Excursion Integrals

We found **no citation** listing "Time-Over-Limit" (degree-minutes) as a standard mapping metric.

**Article Status**: Describe it honestly as your "own engineering proposal inspired by cumulative thermal exposure models (similar to MKT)."

### ❓ Percentiles (P99/P01) as Spot Locators

Percentiles are standard statistical tools but are **not mentioned** in USP, WHO, or ISPE temperature mapping standards.

**Article Status**: Define it as a "general statistical approach adapted to filter sensor noise."

---

## 4. Bibliography for the Article End

```
References:
• USP <1079.4> Temperature Mapping for the Qualification of Storage Areas (USP-NF, official since 2024-05-01)
• USP <1079.2> Mean Kinetic Temperature in the Evaluation of Temperature Excursions (USP-NF, official since 2020-12-01)
• USP <1079.3> Monitoring Devices — Time, Temperature, and Humidity (USP-NF)
• USP <1079> Risks and Mitigation Strategies for the Storage and Transportation of Finished Drug Products (USP-NF)
• WHO Technical Report Series 961, Annex 9, Supplement 8: Temperature mapping of storage areas (May 2015)
• ISPE Good Practice Guide: Controlled Temperature Chambers Version 2.0 (2021)
• ICH Q1A(R2) Stability Testing of New Drug Substances and Products
• FDA 21 CFR Part 11 — Electronic Records; Electronic Signatures
• PIC/S Guide to Good Distribution Practice PE 011-1 (2014)
```
