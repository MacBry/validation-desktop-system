package com.mac.bry.desktop.service.pdf.section;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;

import java.io.File;
import java.util.List;

/**
 * Interfejs renderera konkretnej sekcji w zintegrowanym raporcie PDF.
 */
public interface PdfSectionRenderer {

    /**
     * Renderuje zawartość sekcji do podanego dokumentu.
     *
     * @param document                       Dokument iText do którego dopisywana jest sekcja
     * @param session                        Dane sesji rewalidacji
     * @param activePositions                Posortowana lista aktywnych pozycji (kolumn)
     * @param hypothesisTestingService       Serwis testów statystycznych (opcjonalny, używany w sekcji SPC)
     * @param validationPlanNumberRepository Repozytorium planów (opcjonalne, używane w sekcji tytułowej)
     * @param chartImageFile                 Wygenerowany wykres (opcjonalny, używany w sekcji wizualnej)
     * @param checksum                       Obliczona suma kontrolna SHA-256 (używana w sekcji traceability)
     */
    void renderSection(Document document, 
                       RevalidationSession session, 
                       List<RevalidationSession.GridPosition> activePositions,
                       HypothesisTestingService hypothesisTestingService,
                       ValidationPlanNumberRepository validationPlanNumberRepository,
                       File chartImageFile,
                       String checksum) throws DocumentException;
}
