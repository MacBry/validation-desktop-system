package com.mac.bry.desktop.service.pdf.section;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.mac.bry.desktop.dto.stats.CapabilityIndexes;
import com.mac.bry.desktop.dto.stats.ControlChartData;
import com.mac.bry.desktop.model.ChamberType;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.service.pdf.PdfStyleHelper;
import com.mac.bry.desktop.service.stats.ControlChartCalculator;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;
import com.mac.bry.desktop.service.stats.NelsonRulesDetector;
import com.mac.bry.desktop.service.stats.SensorStatsEngine;
import com.mac.bry.desktop.service.stats.SpcEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StatisticalSectionRenderer implements PdfSectionRenderer {

    @Override
    public void renderSection(Document document, RevalidationSession session, List<RevalidationSession.GridPosition> activePositions,
                              HypothesisTestingService hypothesisTestingService, ValidationPlanNumberRepository validationPlanNumberRepository,
                              File chartImageFile, String checksum) throws DocumentException {

        // 4.1. Analiza Statystyczna oraz Sterowanie Procesem (SPC)
        Paragraph section4_1 = new Paragraph("4.1. Analiza Statystyczna oraz Sterowanie Procesem (SPC)", PdfStyleHelper.getSectionFont());
        section4_1.setSpacingAfter(8);
        document.add(section4_1);

        PdfPTable statsTable = new PdfPTable(10);
        statsTable.setWidthPercentage(100);
        statsTable.setWidths(new float[]{1.7f, 1.5f, 1.1f, 1.1f, 1.1f, 1.1f, 1.1f, 0.9f, 0.9f, 1.2f});
        statsTable.setSpacingAfter(15);

        String[] statsHeaders = {
            "Pozycja", "S/N", "Mediana", "Odch. std.", "RSD", "Skośność", "Kurtoza", "Cp", "Cpk", "JB p-val"
        };
        for (String header : statsHeaders) {
            PdfPCell cell = new PdfPCell(new Phrase(header, PdfStyleHelper.getHeaderFont()));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(4);
            cell.setBackgroundColor(new java.awt.Color(51, 65, 85)); // Slate 600
            statsTable.addCell(cell);
        }

        CoolingChamber chamber = session.getCoolingChamber();
        Double lsl = (chamber != null) ? chamber.getMinOperatingTemp() : null;
        Double usl = (chamber != null) ? chamber.getMaxOperatingTemp() : null;

        List<Double> allCpk = new ArrayList<>();
        List<Double> allStdDev = new ArrayList<>();
        List<Double> allRsd = new ArrayList<>();
        List<Double> allJbPValues = new ArrayList<>();
        List<double[]> allSeriesValues = new ArrayList<>();

        for (RevalidationSession.GridPosition pos : activePositions) {
            RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
            ThermoMeasurementSeries s = d.getSeries();

            double[] values = s.getMeasurements() != null ? s.getMeasurements().stream()
                    .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                    .toArray() : new double[0];
            allSeriesValues.add(values);

            double median = 0.0;
            double stdDev = 0.0;
            double rsd = 0.0;
            double skewness = Double.NaN;
            double kurtosis = Double.NaN;
            double jbPValue = 1.0;
            String cpStr = "–";
            String cpkStr = "–";

            if (values.length >= 2) {
                median = SensorStatsEngine.calculateMedian(values);
                stdDev = SensorStatsEngine.calculateStdDev(values);
                rsd = SensorStatsEngine.calculateRsd(values);
                allStdDev.add(stdDev);
                allRsd.add(rsd);

                if (values.length >= 3) {
                    skewness = SensorStatsEngine.calculateSkewness(values);
                }
                if (values.length >= 4) {
                    kurtosis = SensorStatsEngine.calculateKurtosis(values);
                }
                if (lsl != null && usl != null) {
                    CapabilityIndexes capability = SpcEngine.calculateCapability(values, lsl, usl);
                    allCpk.add(capability.getCpk());
                    cpStr = String.format("%.2f", capability.getCp());
                    cpkStr = String.format("%.2f", capability.getCpk());
                }
                if (values.length >= 5) {
                    jbPValue = hypothesisTestingService.performJarqueBera(values);
                }
                allJbPValues.add(jbPValue);
            }

            statsTable.addCell(PdfStyleHelper.createCell(pos.getLabel(), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_LEFT));
            statsTable.addCell(PdfStyleHelper.createCell(d.getSerialNumber(), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            statsTable.addCell(PdfStyleHelper.createCell(String.format("%.2f°C", median), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            statsTable.addCell(PdfStyleHelper.createCell(String.format("%.3f°C", stdDev), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            statsTable.addCell(PdfStyleHelper.createCell(String.format("%.2f%%", rsd), PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));

            String skewStr = (Double.isNaN(skewness) || Double.isInfinite(skewness)) ? "–" : String.format("%.3f", skewness);
            statsTable.addCell(PdfStyleHelper.createCell(skewStr, PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));

            String kurtStr = (Double.isNaN(kurtosis) || Double.isInfinite(kurtosis)) ? "–" : String.format("%.3f", kurtosis);
            statsTable.addCell(PdfStyleHelper.createCell(kurtStr, PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));

            statsTable.addCell(PdfStyleHelper.createCell(cpStr, PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));
            statsTable.addCell(PdfStyleHelper.createCell(cpkStr, PdfStyleHelper.getCellFont(), java.awt.Color.WHITE, Element.ALIGN_CENTER));

            String jbPValStr = (values.length >= 5) ? String.format("%.4f", jbPValue) : "–";
            java.awt.Color jbBg = (values.length >= 5 && jbPValue < 0.05) ? new java.awt.Color(254, 242, 242) : java.awt.Color.WHITE;
            statsTable.addCell(PdfStyleHelper.createCell(jbPValStr, PdfStyleHelper.getCellFont(), jbBg, Element.ALIGN_CENTER));
        }
        document.add(statsTable);

        // Obliczenie rozstępu przestrzennego
        double sumSpatialRange = 0.0;
        double maxSpatialRange = 0.0;
        int pointsCount = activePositions.isEmpty() ? 0 : session.getAssignedPositions().values().iterator().next().getSeries().getMeasurements().size();
        for (int rowIndex = 0; rowIndex < pointsCount; rowIndex++) {
            double minT = Double.MAX_VALUE;
            double maxT = -Double.MAX_VALUE;
            boolean validRow = false;
            for (RevalidationSession.GridPosition pos : activePositions) {
                RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
                if (d.getSeries() != null && d.getSeries().getMeasurements() != null && rowIndex < d.getSeries().getMeasurements().size()) {
                    double temp = d.getSeries().getMeasurements().get(rowIndex).getRawCelsius();
                    if (temp < minT) minT = temp;
                    if (temp > maxT) maxT = temp;
                    validRow = true;
                }
            }
            if (validRow) {
                double diff = maxT - minT;
                sumSpatialRange += diff;
                if (diff > maxSpatialRange) {
                    maxSpatialRange = diff;
                }
            }
        }
        double meanSpatialRange = pointsCount > 0 ? sumSpatialRange / pointsCount : 0.0;

        // 4.2. Wnioski Statystyczne i Ocena Zdolności GxP
        Paragraph section4_2 = new Paragraph("4.2. Wnioski Statystyczne i Ocena Zdolności GxP", PdfStyleHelper.getSectionFont());
        section4_2.setSpacingBefore(10);
        section4_2.setSpacingAfter(8);
        document.add(section4_2);

        Paragraph conclusions = new Paragraph();
        conclusions.setLeading(14.0f);

        // 1. Zdolność procesu
        conclusions.add(new Chunk("1. Ocena Zdolności Procesu (SPC):\n", PdfStyleHelper.getLabelFont()));
        String capabilityText;
        if (lsl == null || usl == null || allCpk.isEmpty()) {
            capabilityText = "Ze względu na brak zdefiniowanych technologicznych granic temperatury pracy (LSL/USL) dla tej komory, wskaźniki zdolności procesu Cp i Cpk nie mogły zostać wyznaczone.";
        } else {
            double minCpk = allCpk.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            if (minCpk >= 1.33) {
                capabilityText = String.format("Wszystkie punkty pomiarowe charakteryzują się doskonałą zdolnością procesu (minimalny wskaźnik Cpk = %.2f >= 1.33). Ryzyko przekroczenia dopuszczalnych limitów temperatury jest znikome, co świadczy o wysokiej stabilności i precyzji regulacji w komorze.", minCpk);
            } else if (minCpk < 1.0) {
                capabilityText = String.format("OSTRZEŻENIE: Wykryto punkty pomiarowe o krytycznie niskiej zdolności procesu (minimalny wskaźnik Cpk = %.2f < 1.00). Istnieje wysokie ryzyko przekroczenia limitów temperatury (zarówno dolnego, jak i górnego). Zaleca się podjęcie natychmiastowych działań korygujących (np. regulacja nastaw, serwis układu chłodzenia lub relokalizacja czujników).", minCpk);
            } else {
                capabilityText = String.format("Wskaźniki zdolności procesu są akceptowalne (minimalny wskaźnik Cpk = %.2f w przedziale [1.00, 1.33)). Zdolność procesu jest zadowalająca, zaleca się okresowy monitoring stabilności temperatury.", minCpk);
            }
        }
        conclusions.add(new Chunk(capabilityText + "\n\n", PdfStyleHelper.getValueFont()));

        // 2. Stabilność czasowa i normy WHO
        conclusions.add(new Chunk("2. Ocena Stabilności Czasowej (Wytyczne WHO):\n", PdfStyleHelper.getLabelFont()));
        ChamberType type = (chamber != null && chamber.getChamberType() != null) ? chamber.getChamberType() : ChamberType.FRIDGE;
        double limitStdDev = (type == ChamberType.FREEZER || type == ChamberType.LOW_TEMP_FREEZER || type == ChamberType.FREEZE_ROOM) ? 1.0 : 0.3;
        String chamberTypeLabel = (type == ChamberType.FREEZER || type == ChamberType.LOW_TEMP_FREEZER || type == ChamberType.FREEZE_ROOM) ? "zamrażarek/mroźni" : "lodówek/chłodni";

        boolean allStdDevOk = true;
        for (double sd : allStdDev) {
            if (sd > limitStdDev) {
                allStdDevOk = false;
                break;
            }
        }

        String stabilityText;
        if (allStdDevOk) {
            stabilityText = String.format("Odchylenia standardowe (std dev) we wszystkich punktach pomiarowych spełniają wytyczne WHO TRS 961 Supplement 8 (stabilność <= %.1f°C dla %s). Wahania temperatury w czasie są stabilne.", limitStdDev, chamberTypeLabel);
        } else {
            stabilityText = String.format("OSTRZEŻENIE: Wykryto punkty pomiarowe, w których odchylenie standardowe przekracza zalecane przez WHO TRS 961 Supplement 8 limity stabilności (<= %.1f°C dla %s), co wskazuje na niestabilność regulacji czasowej.", limitStdDev, chamberTypeLabel);
        }

        // RSD check for positive temperatures
        double overallMean = 0.0;
        int countPoints = 0;
        for (double[] vals : allSeriesValues) {
            for (double v : vals) {
                overallMean += v;
                countPoints++;
            }
        }
        overallMean = countPoints > 0 ? overallMean / countPoints : 0.0;
        if (overallMean > 0.0) {
            boolean allRsdOk = true;
            for (double r : allRsd) {
                if (r > 5.0) {
                    allRsdOk = false;
                    break;
                }
            }
            if (allRsdOk) {
                stabilityText += " Współczynnik zmienności (RSD) we wszystkich punktach spełnia limit <= 5.0% dla temperatur dodatnich.";
            } else {
                stabilityText += " OSTRZEŻENIE: W wybranych punktach współczynnik zmienności (RSD) przekroczył limit 5.0% wymagany dla temperatur dodatnich.";
            }
        }
        conclusions.add(new Chunk(stabilityText + "\n\n", PdfStyleHelper.getValueFont()));

        // 3. Rozkład temperatur
        conclusions.add(new Chunk("3. Ocena Rozkładu Temperatury (Test Normalności JB):\n", PdfStyleHelper.getLabelFont()));
        long nonNormalCount = allJbPValues.stream().filter(pv -> pv < 0.05).count();
        String normalityText;
        if (nonNormalCount > 0) {
            normalityText = String.format("Test normalności Jarque-Bera wykazuje istotne statystycznie odstępstwa od rozkładu normalnego (p < 0.05) w %d punktach pomiarowych. Może to świadczyć o nieliniowości regulacji temperatury, obecności okresowych cykli rozmrażania (defrostów) lub wymuszonym obiegu powietrza powodującym periodyczne fluktuacje.", nonNormalCount);
        } else {
            normalityText = "Rozkład temperatur we wszystkich punktach pomiarowych nie odbiega w sposób istotny od rozkładu normalnego (p >= 0.05), co wskazuje na stabilne, stochastyczne wahania wokół punktu nastawy bez wyraźnych zakłóceń periodycznych.";
        }
        conclusions.add(new Chunk(normalityText + "\n\n", PdfStyleHelper.getValueFont()));

        // 4. Rozstęp przestrzenny
        conclusions.add(new Chunk("4. Ocena Jednorodności Przestrzennej:\n", PdfStyleHelper.getLabelFont()));
        String spatialText = String.format("Średni rozstęp przestrzenny (jednorodność temperatury w komorze) wynosi %.2f°C, natomiast maksymalny chwilowy rozstęp przestrzenny w czasie trwania sesji wyniósł %.2f°C. Wartości te określają maksymalny gradient temperatury pomiędzy najcieplejszym a najzimniejszym punktem komory chłodniczej i potwierdzają stopień stabilności rozkładu przestrzennego.", meanSpatialRange, maxSpatialRange);
        conclusions.add(new Chunk(spatialText + "\n\n", PdfStyleHelper.getValueFont()));

        // 5. Weryfikacja reguł stabilności Nelsona
        conclusions.add(new Chunk("5. Weryfikacja Stabilności Procesu (Karty Shewharta & Nelson Rules):\n", PdfStyleHelper.getLabelFont()));
        
        int totalXBarViolations = 0;
        int totalSViolations = 0;
        for (RevalidationSession.GridPosition pos : activePositions) {
            RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
            double[] values = d.getSeries().getMeasurements() != null ? d.getSeries().getMeasurements().stream()
                    .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                    .toArray() : new double[0];
            ControlChartData spcData = ControlChartCalculator.calculateShewhartLimits(values);
            totalXBarViolations += NelsonRulesDetector.detectXBarViolations(spcData).size();
            totalSViolations += NelsonRulesDetector.detectSViolations(spcData).size();
        }

        String nelsonConclusionsText;
        if (totalXBarViolations == 0 && totalSViolations == 0) {
            nelsonConclusionsText = "Analiza kart kontrolnych Shewharta (X-bar & S) nie wykazała żadnych naruszeń stabilności procesu na podstawie reguł Nelsona. Świadczy to o stabilności stochastycznej i braku przyczyn systemowych (zakłóceń) wpływających na temperaturę komory.";
        } else {
            nelsonConclusionsText = String.format("OSTRZEŻENIE: Wykryto łącznie %d naruszeń reguł Nelsona dla karty X-bar oraz %d przekroczeń granic na karcie S. Wskazuje to na obecność zmienności o charakterze nielosowym (przyczyn systemowych, np. cykle defrostu, wahania obciążenia komory lub niestabilność układu sterowania). Szczegółowy wykaz przedstawiono w Sekcji 4.3.", totalXBarViolations, totalSViolations);
        }
        conclusions.add(new Chunk(nelsonConclusionsText, PdfStyleHelper.getValueFont()));

        PdfPTable conclusionsBox = new PdfPTable(1);
        conclusionsBox.setWidthPercentage(100);
        conclusionsBox.setSpacingAfter(15);

        PdfPCell boxCell = new PdfPCell(conclusions);
        boxCell.setBackgroundColor(new java.awt.Color(248, 250, 252)); // Slate 50
        boxCell.setPadding(10);
        boxCell.setBorderColor(new java.awt.Color(203, 213, 225)); // Slate 300
        conclusionsBox.addCell(boxCell);

        document.add(conclusionsBox);

        // Nowa strona na weryfikację stabilności Shewharta i reguł Nelsona (Sekcja 4.3)
        document.newPage();
    }
}
