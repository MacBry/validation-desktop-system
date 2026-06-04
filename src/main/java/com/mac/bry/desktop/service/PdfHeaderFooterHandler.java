package com.mac.bry.desktop.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PdfHeaderFooterHandler extends PdfPageEventHelper {
    
    private final Font headerFont;
    private final Font footerFont;
    private final String title;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private PdfTemplate totalPages;

    public PdfHeaderFooterHandler(Font headerFont, Font footerFont, String title) {
        this.headerFont = headerFont;
        this.footerFont = footerFont;
        this.title = title;
    }

    @Override
    public void onOpenDocument(PdfWriter writer, Document document) {
        totalPages = writer.getDirectContent().createTemplate(30, 16);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte cb = writer.getDirectContent();
        
        // Header
        Phrase header = new Phrase(title, headerFont);
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, header,
                (document.right() - document.left()) / 2 + document.leftMargin(),
                document.top() + 10, 0);

        // Footer
        String footerText = String.format("Raport wygenerowany przez Validation Desktop | Data: %s | Strona %d z ", 
                LocalDateTime.now().format(dtf), writer.getPageNumber());
        
        float textSize = footerFont.getBaseFont().getWidthPoint(footerText, footerFont.getSize());
        float textBase = document.bottom() - 20;
        
        cb.beginText();
        cb.setFontAndSize(footerFont.getBaseFont(), footerFont.getSize());
        cb.setTextMatrix(document.left(), textBase);
        cb.showText(footerText);
        cb.endText();
        
        cb.addTemplate(totalPages, document.left() + textSize, textBase);
        
        // Linia oddzielająca stopkę
        cb.moveTo(document.left(), textBase + 10);
        cb.lineTo(document.right(), textBase + 10);
        cb.stroke();
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document document) {
        totalPages.beginText();
        totalPages.setFontAndSize(footerFont.getBaseFont(), footerFont.getSize());
        totalPages.setTextMatrix(0, 0);
        totalPages.showText(String.valueOf(writer.getPageNumber() - 1));
        totalPages.endText();
    }
}
