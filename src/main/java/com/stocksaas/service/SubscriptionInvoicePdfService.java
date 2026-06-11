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
import com.stocksaas.model.Company;
import com.stocksaas.model.CompanySubscriptionRecord;
import com.stocksaas.subscription.PaymentProviderCode;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Génère un PDF de facture pour une souscription validée.
 */
@Service
public class SubscriptionInvoicePdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float MARGIN = 36f;

    public static String buildInvoiceNumber(CompanySubscriptionRecord record) {
        int year = record.getValidatedAt() != null
                ? record.getValidatedAt().getYear()
                : java.time.LocalDateTime.now().getYear();
        return String.format("ABO-%d-%06d", year, record.getId());
    }

    public byte[] generatePdf(CompanySubscriptionRecord record, Company company, String invoiceNumber)
            throws DocumentException {
        Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);
        document.open();

        Font fontTitle = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font fontBold = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font fontNormal = new Font(Font.HELVETICA, 10, Font.NORMAL);
        Font fontSmall = new Font(Font.HELVETICA, 9, Font.NORMAL);

        document.add(new Paragraph("FACTURE D'ABONNEMENT", fontTitle));
        document.add(new Paragraph("Stock SaaS", fontSmall));
        document.add(new Paragraph(" "));

        Paragraph pHeader = new Paragraph();
        pHeader.add(new Chunk("N° ", fontNormal));
        pHeader.add(new Chunk(invoiceNumber, fontBold));
        if (record.getValidatedAt() != null) {
            pHeader.add(new Chunk("     Date : ", fontNormal));
            pHeader.add(new Chunk(record.getValidatedAt().format(DATE_FMT), fontNormal));
        }
        pHeader.add(new Chunk("     Statut : ", fontNormal));
        pHeader.add(new Chunk("Payée", fontBold));
        document.add(pHeader);
        document.add(new Paragraph(" "));

        document.add(new Paragraph("Client", fontBold));
        document.add(new Paragraph(company.getName() != null ? company.getName() : "", fontNormal));
        if (company.getAddress() != null && !company.getAddress().isBlank()) {
            document.add(new Paragraph(company.getAddress(), fontSmall));
        }
        if (company.getEmail() != null && !company.getEmail().isBlank()) {
            document.add(new Paragraph(company.getEmail(), fontSmall));
        }
        if (company.getPhone() != null && !company.getPhone().isBlank()) {
            document.add(new Paragraph("Tel. " + company.getPhone(), fontSmall));
        }
        document.add(new Paragraph(" "));

        document.add(new Paragraph("Detail", fontBold));
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100f);
        table.setWidths(new float[]{3f, 1f, 1.5f, 1.5f});
        table.setSpacingBefore(4f);
        table.setSpacingAfter(8f);

        table.addCell(cell("Designation", fontBold, Element.ALIGN_LEFT));
        table.addCell(cell("Duree", fontBold, Element.ALIGN_RIGHT));
        table.addCell(cell("P.U. (FCFA)", fontBold, Element.ALIGN_RIGHT));
        table.addCell(cell("Montant (FCFA)", fontBold, Element.ALIGN_RIGHT));

        String designation = "Abonnement Stock SaaS — "
                + (record.getPlanLabel() != null ? record.getPlanLabel() : "Standard");
        if (record.getDurationLabel() != null && !record.getDurationLabel().isBlank()) {
            designation += " (" + record.getDurationLabel() + ")";
        }
        String duration = record.getMonths() != null ? record.getMonths() + " mois" : "";
        double amount = record.getAmountPaid() != null ? record.getAmountPaid() : 0d;

        table.addCell(cell(designation, fontNormal, Element.ALIGN_LEFT));
        table.addCell(cell(duration, fontNormal, Element.ALIGN_RIGHT));
        table.addCell(cell(formatMoney(amount), fontNormal, Element.ALIGN_RIGHT));
        table.addCell(cell(formatMoney(amount), fontNormal, Element.ALIGN_RIGHT));

        document.add(table);

        Paragraph pTotal = new Paragraph();
        pTotal.add(new Chunk("Total : ", fontBold));
        pTotal.add(new Chunk(formatMoney(amount) + " FCFA", fontBold));
        document.add(pTotal);
        document.add(new Paragraph(" "));

        if (record.getPaymentProvider() != null && !record.getPaymentProvider().isBlank()) {
            document.add(new Paragraph(
                    "Mode de paiement : " + PaymentProviderCode.label(record.getPaymentProvider()),
                    fontNormal));
        }
        if (record.getPeriodStart() != null && record.getPeriodEnd() != null) {
            document.add(new Paragraph(
                    "Periode couverte : du " + record.getPeriodStart().format(DATE_FMT)
                            + " au " + record.getPeriodEnd().format(DATE_FMT),
                    fontNormal));
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

    private static String formatMoney(double value) {
        return String.format("%,.0f", value).replace(',', ' ');
    }
}
