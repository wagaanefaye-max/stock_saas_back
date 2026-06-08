package com.stocksaas.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Chunk;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stocksaas.dto.InvoiceDTO;
import com.stocksaas.dto.InvoiceLineDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Génère un PDF pour une facture.
 */
@Service
@Slf4j
public class InvoicePdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 36f;

    public byte[] generatePdf(InvoiceDTO invoice) throws DocumentException {
        Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);
        document.open();

        Font fontTitle = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font fontBold = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font fontNormal = new Font(Font.HELVETICA, 10, Font.NORMAL);
        Font fontSmall = new Font(Font.HELVETICA, 9, Font.NORMAL);

        // Titre : FACTURE ou DEVIS selon le statut
        String title = "FACTURE";
        if ("DRAFT".equalsIgnoreCase(invoice.getStatus())) {
            title = "DEVIS";
        }
        document.add(new Paragraph(title, fontTitle));
        document.add(new Paragraph(" "));

        // N° et dates
        Paragraph pHeader = new Paragraph();
        pHeader.add(new Chunk("N° ", fontNormal));
        pHeader.add(new Chunk(invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "", fontBold));
        pHeader.add(new Chunk("     Date : ", fontNormal));
        pHeader.add(new Chunk(invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().format(DATE_FMT) : "", fontNormal));
        if (invoice.getDueDate() != null) {
            pHeader.add(new Chunk("     Echeance : ", fontNormal));
            pHeader.add(new Chunk(invoice.getDueDate().format(DATE_FMT), fontNormal));
        }
        pHeader.add(new Chunk("     Statut : ", fontNormal));
        pHeader.add(new Chunk(invoice.getStatusLabel() != null ? invoice.getStatusLabel() : "", fontBold));
        document.add(pHeader);
        document.add(new Paragraph(" "));

        // Client
        document.add(new Paragraph("Client", fontBold));
        document.add(new Paragraph(invoice.getClientName() != null ? invoice.getClientName() : "", fontNormal));
        if (invoice.getClientAddress() != null && !invoice.getClientAddress().isBlank()) {
            document.add(new Paragraph(invoice.getClientAddress(), fontSmall));
        }
        if (invoice.getClientEmail() != null && !invoice.getClientEmail().isBlank()) {
            document.add(new Paragraph(invoice.getClientEmail(), fontSmall));
        }
        if (invoice.getClientPhone() != null && !invoice.getClientPhone().isBlank()) {
            document.add(new Paragraph("Tel. " + invoice.getClientPhone(), fontSmall));
        }
        document.add(new Paragraph(" "));

        // Tableau des lignes
        document.add(new Paragraph("Detail", fontBold));
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100f);
        table.setWidths(new float[]{3f, 1f, 1.5f, 1.5f});
        table.setSpacingBefore(4f);
        table.setSpacingAfter(8f);

        table.addCell(cell("Designation", fontBold, Element.ALIGN_LEFT));
        table.addCell(cell("Qte", fontBold, Element.ALIGN_RIGHT));
        table.addCell(cell("P.U. (FCFA)", fontBold, Element.ALIGN_RIGHT));
        table.addCell(cell("Montant (FCFA)", fontBold, Element.ALIGN_RIGHT));

        List<InvoiceLineDTO> lines = invoice.getLines();
        if (lines != null) {
            for (InvoiceLineDTO line : lines) {
                table.addCell(cell(line.getDescription() != null ? line.getDescription() : "", fontNormal, Element.ALIGN_LEFT));
                table.addCell(cell(formatQty(line.getQuantity()), fontNormal, Element.ALIGN_RIGHT));
                table.addCell(cell(formatMoney(line.getUnitPrice()), fontNormal, Element.ALIGN_RIGHT));
                table.addCell(cell(formatMoney(line.getAmount()), fontNormal, Element.ALIGN_RIGHT));
            }
        }

        document.add(table);

        // Total
        Paragraph pTotal = new Paragraph();
        pTotal.add(new Chunk("Total : ", fontBold));
        pTotal.add(new Chunk(formatMoney(invoice.getTotal()) + " " + (invoice.getCurrency() != null ? invoice.getCurrency() : "FCFA"), fontBold));
        document.add(pTotal);
        document.add(new Paragraph(" "));

        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            document.add(new Paragraph("Notes", fontBold));
            document.add(new Paragraph(invoice.getNotes(), fontSmall));
        }

        document.close();
        return baos.toByteArray();
    }

    private static PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setBorderColor(Color.LIGHT_GRAY);
        cell.setPadding(4f);
        return cell;
    }

    private static String formatMoney(BigDecimal value) {
        if (value == null) return "0";
        return String.format("%,.2f", value).replace(',', ' ');
    }

    private static String formatQty(BigDecimal value) {
        if (value == null) return "0";
        if (value.stripTrailingZeros().scale() <= 0) {
            return String.valueOf(value.intValue());
        }
        return String.format("%.2f", value);
    }
}
