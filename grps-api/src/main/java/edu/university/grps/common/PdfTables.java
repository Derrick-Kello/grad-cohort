package edu.university.grps.common;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Renders a titled table as PDF for report, eligibility-list and format-report exports. */
public final class PdfTables {

    private static final Color HEADER_BG = new Color(0x1f, 0x4e, 0x79);
    private static final Color STRIPE = new Color(0xf3, 0xf6, 0xf9);

    private PdfTables() {}

    public static byte[] render(String title, List<String> subtitleLines, List<String> header, List<List<String>> rows) {
        boolean wide = header.size() > 6;
        Document doc = new Document(wide ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 42, 42);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0x4a, 0x55, 0x60));
        Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f);
        doc.add(new Paragraph("Graduate Research Progress System", small));
        Paragraph t = new Paragraph(title, titleFont);
        t.setSpacingAfter(4);
        doc.add(t);
        for (String line : subtitleLines) doc.add(new Paragraph(line, small));
        PdfPTable table = new PdfPTable(Math.max(1, header.size()));
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        table.setHeaderRows(1);
        for (String h : header) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headFont));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(4);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }
        int i = 0;
        for (List<String> row : rows) {
            for (int c = 0; c < header.size(); c++) {
                String v = c < row.size() && row.get(c) != null ? row.get(c) : "";
                PdfPCell cell = new PdfPCell(new Phrase(v, cellFont));
                cell.setPadding(4);
                cell.setBorder(Rectangle.BOTTOM);
                cell.setBorderColor(new Color(0xe3, 0xe7, 0xeb));
                if (i % 2 == 1) cell.setBackgroundColor(STRIPE);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(cell);
            }
            i++;
        }
        if (rows.isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase("No rows match the filters.", cellFont));
            cell.setColspan(header.size());
            cell.setPadding(6);
            table.addCell(cell);
        }
        doc.add(table);
        doc.close();
        return out.toByteArray();
    }
}
