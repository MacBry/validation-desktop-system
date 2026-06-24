package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.word.Appendix3DataMapper;
import com.mac.bry.desktop.service.word.Appendix7DataMapper;
import com.mac.bry.desktop.service.word.Appendix8DataMapper;
import com.mac.bry.desktop.service.word.WordTemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Serwis odpowiedzialny za ładowanie szablonu Word DOCX dla Załącznika nr 8, Załącznika nr 3 i Załącznika nr 7,
 * dynamiczną podmianę znaczników GxP i zapis gotowych raportów.
 */
@Service
@Slf4j
public class TestoRevalidationWordService {

    private static final String TEMPLATE_PATH = "/templates/appendix_8_template.docx";
    private static final String TEMPLATE_3_PATH = "/templates/appendix_3_template.docx";
    private static final String TEMPLATE_7_PATH = "/templates/appendix_7_template.docx";

    private final Appendix8DataMapper appendix8DataMapper;
    private final Appendix3DataMapper appendix3DataMapper;
    private final Appendix7DataMapper appendix7DataMapper;

    @Autowired
    public TestoRevalidationWordService(ValidationPlanNumberRepository validationPlanNumberRepository) {
        this.appendix8DataMapper = new Appendix8DataMapper(validationPlanNumberRepository);
        this.appendix3DataMapper = new Appendix3DataMapper(validationPlanNumberRepository);
        this.appendix7DataMapper = new Appendix7DataMapper(validationPlanNumberRepository);
    }

    public void generateAppendix8(RevalidationSession session, OutputStream outputStream) throws Exception {
        log.info("Rozpoczęcie generowania Załącznika nr 8 (Word DOCX) dla urządzenia: {}", 
                session.getCoolingDevice() != null ? session.getCoolingDevice().getInventoryNumber() : "NULL");
        generateAppendix(session, outputStream, TEMPLATE_PATH, appendix8DataMapper.prepareReplacements(session), "Załącznika nr 8");
    }

    public void generateAppendix3(RevalidationSession session, OutputStream outputStream) throws Exception {
        log.info("Rozpoczęcie generowania Załącznika nr 3 (Word DOCX) dla urządzenia: {}", 
                session.getCoolingDevice() != null ? session.getCoolingDevice().getInventoryNumber() : "NULL");
        generateAppendix(session, outputStream, TEMPLATE_3_PATH, appendix3DataMapper.prepareReplacements(session), "Załącznika nr 3");
    }

    public void generateAppendix7(RevalidationSession session, OutputStream outputStream) throws Exception {
        log.info("Rozpoczęcie generowania Załącznika nr 7 (Word DOCX) dla urządzenia: {}", 
                session.getCoolingDevice() != null ? session.getCoolingDevice().getInventoryNumber() : "NULL");
        generateAppendix(session, outputStream, TEMPLATE_7_PATH, appendix7DataMapper.prepareReplacements(session), "Załącznika nr 7");
    }

    private void generateAppendix(RevalidationSession session, OutputStream outputStream, String templatePath, Map<String, String> replacements, String name) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(templatePath)) {
            if (is == null) {
                log.warn("Szablon {} nie został odnaleziony pod ścieżką: {}. Generowanie dokumentu zastępczego (demo/showcase).", name, templatePath);
                try (XWPFDocument doc = generateFallbackDocument(name, templatePath, replacements)) {
                    doc.write(outputStream);
                    log.info("Dokument zastępczy dla {} został pomyślnie wygenerowany i zapisany.", name);
                }
                return;
            }

            try (XWPFDocument doc = new XWPFDocument(is)) {
                WordTemplateEngine.replacePlaceholders(doc, replacements);
                doc.write(outputStream);
                log.info("{} został pomyślnie wygenerowany i zapisany.", name);
            }
        }
    }

    XWPFDocument generateFallbackDocument(String name, String templatePath, Map<String, String> replacements) {
        XWPFDocument doc = new XWPFDocument();

        // 1. Nagłówek / Tytuł dokumentu pokazowego
        XWPFParagraph titlePara = doc.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText("DOKUMENTACJA ZASTĘPCZA (MOCK) - " + name.toUpperCase());
        titleRun.setBold(true);
        titleRun.setFontSize(16);
        titleRun.setColor("D32F2F"); // Czerwony akcent premium

        XWPFParagraph subPara = doc.createParagraph();
        subPara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun subRun = subPara.createRun();
        subRun.setText("Wygenerowano automatycznie w trybie pokazowym z powodu braku oficjalnego szablonu.");
        subRun.setItalic(true);
        subRun.setFontSize(10);
        subRun.setColor("555555");

        XWPFParagraph pathPara = doc.createParagraph();
        pathPara.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun pathRun = pathPara.createRun();
        pathRun.setText("Brakujący plik szablonu: " + templatePath);
        pathRun.setFontSize(9);
        pathRun.setColor("777777");

        doc.createParagraph(); // Odstęp

        XWPFParagraph infoPara = doc.createParagraph();
        XWPFRun infoRun = infoPara.createRun();
        infoRun.setText("Poniższa tabela zawiera zestawienie dynamicznych parametrów i znaczników GxP, które zostały podstawione pod odpowiednie pola raportu:");
        infoRun.setFontSize(11);

        doc.createParagraph();

        // 2. Utworzenie tabeli parametrów
        XWPFTable table = doc.createTable();
        
        // Nagłówek tabeli
        XWPFTableRow headerRow = table.getRow(0);
        XWPFTableCell cellKeyHeader = headerRow.getCell(0);
        cellKeyHeader.setText("Klucz znacznika");
        cellKeyHeader.setColor("EEEEEE");
        
        XWPFTableCell cellValueHeader = headerRow.createCell();
        cellValueHeader.setText("Wartość podstawiona");
        cellValueHeader.setColor("EEEEEE");

        // Dodanie wierszy dla każdego parametru z replacements
        java.util.List<String> sortedKeys = new java.util.ArrayList<>(replacements.keySet());
        java.util.Collections.sort(sortedKeys);

        for (String key : sortedKeys) {
            XWPFTableRow row = table.createRow();
            
            // Kolumna 1: Klucz bez znaków $ i % (aby testy i silnik nie uznały tego za niepodmieniony znacznik)
            String cleanKey = key.replace("$", "").replace("%", "");
            XWPFTableCell cell1 = row.getCell(0);
            if (cell1 == null) {
                cell1 = row.createCell();
            }
            cell1.setText(cleanKey);
            
            // Kolumna 2: Surowy klucz znacznikowy (zostanie podmieniony przez WordTemplateEngine)
            XWPFTableCell cell2 = row.getCell(1);
            if (cell2 == null) {
                cell2 = row.createCell();
            }
            cell2.setText(key);
        }

        // 3. Uruchomienie WordTemplateEngine na wygenerowanym dokumencie zastępczym
        WordTemplateEngine.replacePlaceholders(doc, replacements);

        return doc;
    }
}
