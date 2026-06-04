package com.mac.bry.desktop.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.mac.bry.desktop.dto.UserAuditDto;
import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class ExportService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void exportToPdf(List<UserAuditDto> data, File file, String title) throws IOException {
        log.info("Eksportowanie {} rekordów do PDF (GMP format): {}", data.size(), file.getAbsolutePath());
        
        try (Document document = new Document(PageSize.A4.rotate())) {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));
            
            // Czcionki z obsługą polskich znaków (Arial)
            BaseFont baseFont = BaseFont.createFont("C:\\Windows\\Fonts\\arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            Font titleFont = new Font(baseFont, 16, Font.BOLD, java.awt.Color.BLACK);
            Font headerFont = new Font(baseFont, 10, Font.BOLD, java.awt.Color.WHITE);
            Font cellFont = new Font(baseFont, 9, Font.NORMAL, java.awt.Color.BLACK);
            Font footerFont = new Font(baseFont, 8, Font.ITALIC, java.awt.Color.GRAY);

            // Ustawienie handlera nagłówka/stopki
            PdfHeaderFooterHandler handler = new PdfHeaderFooterHandler(titleFont, footerFont, title);
            writer.setPageEvent(handler);

            // Metadane PDF
            document.addTitle(title);
            document.addAuthor("Validation Desktop System");
            document.addCreator("macie - Deepmind coding agent");
            document.addCreationDate();
            
            document.setMargins(36, 36, 54, 54); // L, R, T, B
            document.open();

            // Tabela
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1, 3, 2, 2, 2, 3, 3});
            table.setHeaderRows(1);

            // Nagłówki
            String[] headers = {"ID", "Data i Godzina", "Wykonawca", "Operacja", "Pole", "Stara Wartość", "Nowa Wartość"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(8);
                cell.setBackgroundColor(new java.awt.Color(44, 62, 80)); // Professional dark blue
                cell.setBorderColor(java.awt.Color.DARK_GRAY);
                table.addCell(cell);
            }

            // Dane
            int rowIndex = 0;
            for (UserAuditDto dto : data) {
                java.awt.Color bgColor = (rowIndex % 2 == 0) ? java.awt.Color.WHITE : new java.awt.Color(245, 245, 245);
                
                table.addCell(createCell(String.valueOf(dto.getRevisionId()), cellFont, bgColor));
                table.addCell(createCell(dto.getTimestamp().format(DTF), cellFont, bgColor));
                table.addCell(createCell(dto.getModifiedBy(), cellFont, bgColor));
                table.addCell(createCell(dto.getOperationType(), cellFont, bgColor));
                table.addCell(createCell(dto.getFieldName(), cellFont, bgColor));
                table.addCell(createCell(dto.getOldValue() != null ? dto.getOldValue() : "-", cellFont, bgColor));
                table.addCell(createCell(dto.getNewValue() != null ? dto.getNewValue() : "-", cellFont, bgColor));
                
                rowIndex++;
            }

            document.add(table);
            
        } catch (Exception e) {
            log.error("Błąd podczas generowania PDF", e);
            throw new IOException("Błąd generowania PDF (polskie znaki): " + e.getMessage());
        }
    }

    private PdfPCell createCell(String text, Font font, java.awt.Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(java.awt.Color.LIGHT_GRAY);
        return cell;
    }

    public void exportToCsv(List<UserAuditDto> data, File file) throws IOException {
        log.info("Eksportowanie {} rekordów do CSV: {}", data.size(), file.getAbsolutePath());
        
        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            // Nagłówek
            writer.writeNext(new String[]{"Revision ID", "Timestamp", "Modified By", "Operation", "Field", "Old Value", "New Value"});
            
            // Dane
            for (UserAuditDto dto : data) {
                writer.writeNext(new String[]{
                        String.valueOf(dto.getRevisionId()),
                        dto.getTimestamp().format(DTF),
                        dto.getModifiedBy(),
                        dto.getOperationType(),
                        dto.getFieldName(),
                        dto.getOldValue(),
                        dto.getNewValue()
                });
            }
        }
    }
}
