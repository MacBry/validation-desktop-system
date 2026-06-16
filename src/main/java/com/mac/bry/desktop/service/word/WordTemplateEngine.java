package com.mac.bry.desktop.service.word;

import org.apache.poi.xwpf.usermodel.*;

import java.util.Map;

public class WordTemplateEngine {

    /**
     * Wyszukuje i podmienia znaczniki w całym dokumencie Word (akapity, tabele, nagłówki, stopki).
     */
    public static void replacePlaceholders(XWPFDocument doc, Map<String, String> replacements) {
        // Podmiana w głównych akapitach tekstu
        for (XWPFParagraph p : doc.getParagraphs()) {
            replaceInParagraph(p, replacements);
        }

        // Podmiana w tabelach i komórkach tabel
        for (XWPFTable table : doc.getTables()) {
            replaceInTable(table, replacements);
        }

        // Podmiana w nagłówkach
        for (XWPFHeader header : doc.getHeaderList()) {
            for (XWPFParagraph p : header.getParagraphs()) {
                replaceInParagraph(p, replacements);
            }
            for (XWPFTable table : header.getTables()) {
                replaceInTable(table, replacements);
            }
        }

        // Podmiana w stopkach
        for (XWPFFooter footer : doc.getFooterList()) {
            for (XWPFParagraph p : footer.getParagraphs()) {
                replaceInParagraph(p, replacements);
            }
            for (XWPFTable table : footer.getTables()) {
                replaceInTable(table, replacements);
            }
        }
    }

    /**
     * Przeszukuje i podmienia tekst w komórkach tabeli (w tym zagnieżdżonych).
     */
    private static void replaceInTable(XWPFTable table, Map<String, String> replacements) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    replaceInParagraph(p, replacements);
                }
                // Rekurencja dla zagnieżdżonych tabel w komórce
                for (XWPFTable nestedTable : cell.getTables()) {
                    replaceInTable(nestedTable, replacements);
                }
            }
        }
    }

    /**
     * Realizuje podmianę znaczników w pojedynczym akapicie, zabezpieczając przed rozbijaniem 
     * tokenów przez formatowanie MS Word (Split Run Solution).
     */
    private static void replaceInParagraph(XWPFParagraph p, Map<String, String> replacements) {
        String text = p.getText();
        if (text == null || (!text.contains("$") && !text.contains("%"))) {
            return;
        }

        boolean updated = false;
        String newText = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String placeholder = entry.getKey();
            String replacement = entry.getValue();
            if (replacement == null) {
                continue; // Pomijamy wpisy z niezdefiniowaną wartością
            }
            if (newText.contains(placeholder)) {
                newText = newText.replace(placeholder, replacement);
                updated = true;
            }
        }

        if (updated) {
            // Usuwamy wszystkie dotychczasowe podfragmenty (runs) za wyjątkiem pierwszego
            int runSize = p.getRuns().size();
            for (int i = runSize - 1; i > 0; i--) {
                p.removeRun(i);
            }
            
            // Wpisujemy scalony, podmieniony tekst do pierwszego runu (lub tworzymy go, jeśli nie było)
            if (!p.getRuns().isEmpty()) {
                p.getRuns().get(0).setText(newText, 0);
            } else {
                p.createRun().setText(newText);
            }
        }
    }
}
