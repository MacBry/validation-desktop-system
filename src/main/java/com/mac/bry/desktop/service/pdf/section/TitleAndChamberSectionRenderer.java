package com.mac.bry.desktop.service.pdf.section;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.mac.bry.desktop.model.GxPProcedureType;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ValidationPlanNumber;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.helper.MappingValidator;
import com.mac.bry.desktop.service.pdf.PdfStyleHelper;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;

import java.io.File;
import java.util.List;

public class TitleAndChamberSectionRenderer implements PdfSectionRenderer {

    @Override
    public void renderSection(Document document, RevalidationSession session, List<RevalidationSession.GridPosition> activePositions,
                              HypothesisTestingService hypothesisTestingService, ValidationPlanNumberRepository validationPlanNumberRepository,
                              File chartImageFile, String checksum) throws DocumentException {

        // Pobranie numeru RPW
        String rpwFormatted = "–";
        if (validationPlanNumberRepository != null && session.getCoolingDevice() != null) {
            List<ValidationPlanNumber> planNumbers = validationPlanNumberRepository.findByCoolingDeviceOrderByYearDesc(session.getCoolingDevice());
            if (!planNumbers.isEmpty()) {
                rpwFormatted = planNumbers.get(0).getFormattedRpw();
            }
        }

        // 1. TYTUŁ I METRYKA WIZYTY WALIDACYJNEJ
        String reportTitle = session.getProcedureType() == GxPProcedureType.MAPPING
                ? "RAPORT Z MAPOWANIA PRZESTRZENNEGO ROZKŁADU TEMPERATUR (PDA TR-64)"
                : "RAPORT Z REWALIDACJI PRZESTRZENNEJ ROZKŁADU TEMPERATUR";
        Paragraph title = new Paragraph(reportTitle, PdfStyleHelper.getTitleFont());
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(15);
        document.add(title);

        Paragraph section1 = new Paragraph("1. Charakterystyka Celu Walidacji (Komora Chłodnicza)", PdfStyleHelper.getSectionFont());
        section1.setSpacingAfter(8);
        document.add(section1);

        PdfPTable chamberTable = new PdfPTable(4);
        chamberTable.setWidthPercentage(100);
        chamberTable.setWidths(new float[]{2, 3, 2, 3});
        chamberTable.setSpacingAfter(15);

        chamberTable.addCell(PdfStyleHelper.createMetaCell("Urządzenie:", PdfStyleHelper.getLabelFont(), true));
        chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingDevice().getName(), PdfStyleHelper.getValueFont(), false));
        chamberTable.addCell(PdfStyleHelper.createMetaCell("Nr inwentarzowy:", PdfStyleHelper.getLabelFont(), true));
        chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingDevice().getInventoryNumber(), PdfStyleHelper.getValueFont(), false));

        chamberTable.addCell(PdfStyleHelper.createMetaCell("Nazwa komory:", PdfStyleHelper.getLabelFont(), true));
        chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingChamber().getChamberName(), PdfStyleHelper.getValueFont(), false));
        chamberTable.addCell(PdfStyleHelper.createMetaCell("Typ komory:", PdfStyleHelper.getLabelFont(), true));
        chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingChamber().getChamberType().getDisplayName(), PdfStyleHelper.getValueFont(), false));

        chamberTable.addCell(PdfStyleHelper.createMetaCell("Zakres temp. pracy:", PdfStyleHelper.getLabelFont(), true));
        chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingChamber().getFormattedMinOperatingTemp() + " do " + session.getCoolingChamber().getFormattedMaxOperatingTemp(), PdfStyleHelper.getValueFont(), false));
        chamberTable.addCell(PdfStyleHelper.createMetaCell("Kubatura komory:", PdfStyleHelper.getLabelFont(), true));
        chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingChamber().getFormattedVolume() + " (" + session.getCoolingChamber().getVolumeCategoryDisplayName() + ")", PdfStyleHelper.getValueFont(), false));

        chamberTable.addCell(PdfStyleHelper.createMetaCell("Przechowywany materiał:", PdfStyleHelper.getLabelFont(), true));
        chamberTable.addCell(PdfStyleHelper.createMetaCell(session.getCoolingChamber().getMaterialName() + " (wymaga mapowania: " + (session.getCoolingChamber().isMappingRequired() ? "TAK" : "NIE") + ")", PdfStyleHelper.getValueFont(), false));
        chamberTable.addCell(PdfStyleHelper.createMetaCell("Numer planu (RPW):", PdfStyleHelper.getLabelFont(), true));
        chamberTable.addCell(PdfStyleHelper.createMetaCell(rpwFormatted, PdfStyleHelper.getValueFont(), false));

        if (session.getProcedureType() == GxPProcedureType.MAPPING) {
            MappingValidator.MappingResult mappingResult = MappingValidator.validate(session);
            String hotspotLabel = (mappingResult.isSuccess() && mappingResult.getHotspot() != null) ? mappingResult.getHotspot().getLabel() : "Niedostępne";
            String coldspotLabel = (mappingResult.isSuccess() && mappingResult.getColdspot() != null) ? mappingResult.getColdspot().getLabel() : "Niedostępne";
            chamberTable.addCell(PdfStyleHelper.createMetaCell("Wyznaczony Hotspot:", PdfStyleHelper.getLabelFont(), true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell(hotspotLabel, PdfStyleHelper.getValueFont(), false));
            chamberTable.addCell(PdfStyleHelper.createMetaCell("Wyznaczony Coldspot:", PdfStyleHelper.getLabelFont(), true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell(coldspotLabel, PdfStyleHelper.getValueFont(), false));
        } else if (session.getCoolingChamber().isMappingRequired()) {
            String mappingDate = session.getCoolingChamber().getLastMappingDate() != null ? session.getCoolingChamber().getLastMappingDate().toString() : "Brak";
            String hotspotLabel = session.getCoolingChamber().getHotspotPosition() != null ? session.getCoolingChamber().getHotspotPosition().getLabel() : "Brak";
            String coldspotLabel = session.getCoolingChamber().getColdspotPosition() != null ? session.getCoolingChamber().getColdspotPosition().getLabel() : "Brak";
            chamberTable.addCell(PdfStyleHelper.createMetaCell("Referencyjne mapowanie:", PdfStyleHelper.getLabelFont(), true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell(mappingDate, PdfStyleHelper.getValueFont(), false));
            chamberTable.addCell(PdfStyleHelper.createMetaCell("Punkty krytyczne (H/C):", PdfStyleHelper.getLabelFont(), true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell("Hotspot: " + hotspotLabel + "\nColdspot: " + coldspotLabel, PdfStyleHelper.getValueFont(), false));
        } else {
            chamberTable.addCell(PdfStyleHelper.createMetaCell("Wymóg mapowania:", PdfStyleHelper.getLabelFont(), true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell("NIE dotyczy", PdfStyleHelper.getValueFont(), false));
            chamberTable.addCell(PdfStyleHelper.createMetaCell("", PdfStyleHelper.getLabelFont(), true));
            chamberTable.addCell(PdfStyleHelper.createMetaCell("", PdfStyleHelper.getValueFont(), false));
        }

        document.add(chamberTable);
    }
}
