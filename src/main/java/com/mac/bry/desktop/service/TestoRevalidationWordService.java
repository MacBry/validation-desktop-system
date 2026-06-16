package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.word.Appendix3DataMapper;
import com.mac.bry.desktop.service.word.Appendix7DataMapper;
import com.mac.bry.desktop.service.word.Appendix8DataMapper;
import com.mac.bry.desktop.service.word.WordTemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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
                throw new FileNotFoundException("Nie odnaleziono szablonu " + name + " pod ścieżką w zasobach: " + templatePath);
            }

            try (XWPFDocument doc = new XWPFDocument(is)) {
                WordTemplateEngine.replacePlaceholders(doc, replacements);
                doc.write(outputStream);
                log.info("{} został pomyślnie wygenerowany i zapisany.", name);
            }
        }
    }
}
