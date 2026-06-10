package com.mac.bry.desktop.service.pdf;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.mac.bry.desktop.model.RevalidationSession;

import java.io.IOException;

public class PdfStyleHelper {

    private static BaseFont baseFont;

    static {
        try {
            baseFont = BaseFont.createFont("C:\\Windows\\Fonts\\arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (IOException e) {
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1250, BaseFont.EMBEDDED);
            } catch (Exception ex) {
                baseFont = null;
            }
        }
    }

    public static BaseFont getBaseFont() {
        return baseFont;
    }

    public static Font getFont(float size, int style, java.awt.Color color) {
        if (baseFont != null) {
            return new Font(baseFont, size, style, color);
        }
        return new Font(Font.HELVETICA, size, style, color);
    }

    public static Font getTitleFont() {
        return getFont(15, Font.BOLD, new java.awt.Color(31, 58, 86));
    }

    public static Font getSectionFont() {
        return getFont(11, Font.BOLD, new java.awt.Color(44, 62, 80));
    }

    public static Font getLabelFont() {
        return getFont(9, Font.BOLD, java.awt.Color.BLACK);
    }

    public static Font getValueFont() {
        return getFont(9, Font.NORMAL, java.awt.Color.BLACK);
    }

    public static Font getHeaderFont() {
        return getFont(8, Font.BOLD, java.awt.Color.WHITE);
    }

    public static Font getCellFont() {
        return getFont(8, Font.NORMAL, java.awt.Color.BLACK);
    }

    public static Font getFooterFont() {
        return getFont(8, Font.ITALIC, java.awt.Color.GRAY);
    }

    public static PdfPCell createMetaCell(String text, Font font, boolean isLabel) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setBorderColor(java.awt.Color.LIGHT_GRAY);
        if (isLabel) {
            cell.setBackgroundColor(new java.awt.Color(241, 245, 249)); // Slate 100
        } else {
            cell.setBackgroundColor(java.awt.Color.WHITE);
        }
        return cell;
    }

    public static PdfPCell createCell(String text, Font font, java.awt.Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(new java.awt.Color(226, 232, 240)); // Slate 200
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    public static String getShortCode(RevalidationSession.GridPosition pos) {
        switch (pos) {
            case TOP_FRONT_LEFT: return "G-PL";
            case TOP_FRONT_RIGHT: return "G-PP";
            case TOP_BACK_LEFT: return "G-TL";
            case TOP_BACK_RIGHT: return "G-TP";
            case BOTTOM_FRONT_LEFT: return "D-PL";
            case BOTTOM_FRONT_RIGHT: return "D-PP";
            case BOTTOM_BACK_LEFT: return "D-TL";
            case BOTTOM_BACK_RIGHT: return "D-TP";
            default: return pos.name();
        }
    }
}
