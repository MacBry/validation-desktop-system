package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TestoCsvExportService {

    public void exportToCsv(File targetFile, String model, String serialNumber, String battery, String interval, int count, String comments, List<ThermoMeasurementPoint> points) throws IOException {
        try (FileWriter writer = new FileWriter(targetFile)) {
            writer.write("Validation System - Standalone Testo Report\n");
            writer.write("Wygenerowano," + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            writer.write("Model rejestratora," + (model != null ? model : "") + "\n");
            writer.write("Numer seryjny," + (serialNumber != null ? serialNumber : "") + "\n");
            writer.write("Stan baterii," + (battery != null ? battery : "") + "\n");
            writer.write("Interwal probkowania," + (interval != null ? interval : "") + "\n");
            writer.write("Liczba punktow," + count + "\n");
            if (comments != null && !comments.isBlank()) {
                writer.write("Uwagi," + comments.replace("\n", " ").replace(",", ";") + "\n");
            }
            writer.write("\n");
            
            writer.write("Lp.,Czas Lokalny Pomiaru,Temperatura [C]\n");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (ThermoMeasurementPoint pt : points) {
                writer.write(pt.getMeasurementIndex() + "," 
                        + pt.getTimestampLocal().format(timeFormatter) + "," 
                        + pt.getRawCelsius() + "\n");
            }
        }
    }
}
