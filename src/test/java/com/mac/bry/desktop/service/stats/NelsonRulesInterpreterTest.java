package com.mac.bry.desktop.service.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NelsonRulesInterpreterTest {

    @Test
    void testXBarInterpretation() {
        String r1 = NelsonRulesInterpreter.getXBarInterpretation(1);
        assertNotNull(r1);
        assertTrue(r1.contains("nagłym") || r1.contains("otwarcie drzwi"));

        String r2 = NelsonRulesInterpreter.getXBarInterpretation(2);
        assertNotNull(r2);
        assertTrue(r2.contains("Trwałe przesunięcie"));

        String r3 = NelsonRulesInterpreter.getXBarInterpretation(3);
        assertNotNull(r3);
        assertTrue(r3.contains("trend"));

        String r4 = NelsonRulesInterpreter.getXBarInterpretation(4);
        assertNotNull(r4);
        assertTrue(r4.contains("Niestabilność oscylacyjna"));

        String rUnknown = NelsonRulesInterpreter.getXBarInterpretation(99);
        assertNotNull(rUnknown);
        assertTrue(rUnknown.contains("Niezdefiniowana"));
    }

    @Test
    void testSChartInterpretation() {
        String s1 = NelsonRulesInterpreter.getSChartInterpretation(1);
        assertNotNull(s1);
        assertTrue(s1.contains("zmienności") && s1.contains("turbulencji"));

        String sUnknown = NelsonRulesInterpreter.getSChartInterpretation(2);
        assertNotNull(sUnknown);
        assertTrue(sUnknown.contains("Niezdefiniowana"));
    }
}
