# Technical Specification: Generator Raportów i Paczka ZIP (Jednostka 3)

## 1. Integracja Szablonu Word i Serwis Podmiany Tagi (Apache POI)

Wdrożony zostanie nowy serwis `TestoTransportWordService.java` do obsługi Załącznika nr 4. Będzie on ładował szablon z zasobów: `/templates/appendix_4_template.docx`.

```java
package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.TransportValidationSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class TestoTransportWordService {

    private static final String TEMPLATE_4_PATH = "/templates/appendix_4_template.docx";

    public void generateAppendix4(TransportValidationSession session, OutputStream os) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(TEMPLATE_4_PATH)) {
            if (is == null) {
                throw new FileNotFoundException("Nie odnaleziono szablonu Załącznika nr 4: " + TEMPLATE_4_PATH);
            }

            try (XWPFDocument doc = new XWPFDocument(is)) {
                Map<String, String> replacements = prepareReplacements(session);
                
                // Wykorzystanie istniejącej logiki podmiany (XWPFRun)
                replacePlaceholders(doc, replacements);
                
                doc.write(os);
            }
        }
    }

    private Map<String, String> prepareReplacements(TransportValidationSession session) {
        Map<String, String> replacements = new HashMap<>();
        
        replacements.put("$dzial$", session.getTransportUnit().getDepartmentName());
        replacements.put("$trasa$", session.getTransportRoute().getName());
        replacements.put("$dataTransportu$", session.getSessionDate().toString());
        replacements.put("$nazwaUrzadzenia$", session.getTransportUnit().getName());
        replacements.put("$numerSeryjnyUrzadzenia$", session.getTransportUnit().getInventoryNumber());
        
        // Zależnie od kalkulacji Hold-Time z Jednostki 2
        String holdTimeText = session.isPowerFailureTest() 
                ? session.getCalculatedHoldTimeMinutes() + " minut" 
                : "Nie dotyczy";
        replacements.put("$calculatedHoldTime$", holdTimeText);
        
        // Decyzja GxP
        replacements.put("$tak$", session.isSuccess() ? "[X]" : "");
        replacements.put("$nie$", session.isSuccess() ? "" : "[X]");
        replacements.put("$Wnioski$", session.getConclusions());
        replacements.put("$Uwagi$", session.getRemarks());

        return replacements;
    }
}
```

---

## 2. Architektura Kompilatora ZIP (`TransportZipCompiler.java`)

Nowy komponent `TransportZipCompiler` zintegruje wszystkie powiązane zasoby do jednego spakowanego archiwum:

```java
package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.TransportValidationSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class TransportZipCompiler {

    private final TestoTransportWordService wordService;
    private final TestoRevalidationWordService revalidationWordService; // Do Załącznika 8
    private final TestoPdfReportService pdfReportService; // Do wykresów PDF

    public void compileTransportPackage(TransportValidationSession session, File outputZip) throws Exception {
        File tempDocx4 = File.createTempFile("Zalacznik_nr_4_", ".docx");
        File tempDocx8 = File.createTempFile("Zalacznik_nr_8_", ".docx");
        
        try {
            // 1. Generuj Załącznik nr 4
            try (FileOutputStream fos = new FileOutputStream(tempDocx4)) {
                wordService.generateAppendix4(session, fos);
            }
            
            // 2. Generuj Załącznik nr 8 (Rozmieszczenie sensorów w aucie)
            try (FileOutputStream fos = new FileOutputStream(tempDocx8)) {
                revalidationWordService.generateAppendix8(session.getRevalidationSession(), fos);
            }

            // 3. Zapisz do ZIP
            try (FileOutputStream zipFos = new FileOutputStream(outputZip);
                 ZipOutputStream zos = new ZipOutputStream(zipFos)) {
                 
                 addFileToZip(zos, tempDocx4, "Zalacznik_nr_4_Raport_z_walidacji_warunkow_transportu.docx");
                 addFileToZip(zos, tempDocx8, "Zalacznik_nr_8_Graficzny_schemat_rozmieszczenia_czujnikow.docx");
                 
                 // Dodaj indywidualne wykresy i certyfikaty dla 1-2 sensorów
                 // ... logika analogiczna do RevalidationZipCompiler
            }
            
        } finally {
            if (tempDocx4.exists()) tempDocx4.delete();
            if (tempDocx8.exists()) tempDocx8.delete();
        }
    }
}
```
---
## 3. Ścieżki szablonów w zasobach
* `/templates/appendix_4_template.docx` (Nowy szablon Załącznika nr 4 dostarczony przez użytkownika).
