package io.beep.core.adapter.out.billing;

import io.beep.core.app.port.InvoicePdfPort;
import io.beep.core.domain.billing.BillingCycle;
import io.beep.core.domain.billing.Invoice;
import io.beep.core.domain.billing.PaymentMethod;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Facture PDF 1.4 d'une page A4, en français, sans dépendance : police Helvetica standard (WinAnsi). Mentions légales du vendeur
 * (raison sociale, adresse, numéro d'identification) fournies par la configuration : elles doivent être renseignées avant la production.
 */
public class InvoicePdfAdapter implements InvoicePdfPort {
  public record Seller(String name, String address, String taxId) {}

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT).withZone(ZoneOffset.UTC);

  private final Seller seller;

  public InvoicePdfAdapter(Seller seller) {
    this.seller = seller;
  }

  @Override
  public byte[] render(Invoice i) {
    StringBuilder c = new StringBuilder();
    // Bandeau et titre
    c.append("0.74 0.21 0.09 rg 40 780 515 4 re f\n");
    text(c, 40, 800, 22, true, "beep");
    text(c, 555 - width("FACTURE", 16, true), 800, 16, true, "FACTURE");
    text(c, 555 - width(i.number(), 11, false), 764, 11, false, i.number());

    text(c, 40, 750, 9, true, "ÉMETTEUR");
    int y = 736;
    for (String line : new String[] {seller.name(), seller.address(), seller.taxId().isBlank() ? "" : "N° d'identification : " + seller.taxId()}) {
      if (!line.isBlank()) {
        text(c, 40, y, 10, false, line);
        y -= 14;
      }
    }
    text(c, 320, 750, 9, true, "FACTURÉ À");
    text(c, 320, 736, 10, false, i.customerName());
    text(c, 320, 722, 10, false, i.customerEmail());
    text(c, 320, 708, 10, false, "Pays : " + i.country());

    text(c, 40, 660, 10, false, "Date d'émission : " + DATE.format(i.issuedAt()));
    text(c, 40, 646, 10, false, "Période : du " + DATE.format(i.periodStart()) + " au " + DATE.format(i.periodEnd()));
    text(c, 40, 632, 10, false, "Mode de paiement : " + method(i.method()));

    // Tableau
    c.append("0.95 0.94 0.90 rg 40 596 515 22 re f 0 0 0 rg\n");
    text(c, 48, 603, 10, true, "Désignation");
    text(c, 555 - 8 - width("Montant (FCFA)", 10, true), 603, 10, true, "Montant (FCFA)");
    int row = 578;
    row = line(c, row, "Abonnement BeepClick " + i.planName() + " — " + cycle(i.cycle()), money(i.listPriceXaf()));
    if (i.discountXaf() > 0) row = line(c, row, "Remise (code promotionnel)", "-" + money(i.discountXaf()));
    if (i.creditXaf() > 0) row = line(c, row, "Crédit du temps restant de l'abonnement précédent", "-" + money(i.creditXaf()));
    c.append("0.8 0.8 0.78 RG 40 ").append(row + 10).append(" m 555 ").append(row + 10).append(" l S\n");
    row -= 8;
    row = line(c, row, "Total HT", money(i.netXaf()));
    row = line(c, row, "TVA " + rate(i.vatRateBp()), money(i.vatXaf()));
    text(c, 48, row, 12, true, "Total TTC");
    String total = money(i.grossXaf()) + " FCFA";
    text(c, 555 - 8 - width(total, 12, true), row, 12, true, total);

    text(c, 40, 80, 8, false, "Les montants sont exprimés en francs CFA (XAF), TVA incluse dans le prix affiché. Facture acquittée : paiement reçu.");
    text(c, 40, 68, 8, false, "Document généré automatiquement par BeepClick, valable sans signature.");
    return document(c.toString());
  }

  private static int line(StringBuilder c, int y, String label, String amount) {
    text(c, 48, y, 10, false, label);
    text(c, 555 - 8 - width(amount, 10, false), y, 10, false, amount);
    return y - 18;
  }

  private static String method(PaymentMethod m) {
    return switch (m) {
      case MTN_MOMO -> "MTN Mobile Money";
      case ORANGE_MONEY -> "Orange Money";
      case CARD -> "Carte bancaire";
      case MANUAL -> "Virement / espèces";
    };
  }

  private static String cycle(BillingCycle c) {
    return switch (c) {
      case MONTHLY -> "1 mois";
      case QUARTERLY -> "3 mois";
      case ANNUAL -> "12 mois";
    };
  }

  private static String rate(int bp) {
    return String.format(Locale.FRANCE, "%.2f %%", bp / 100.0).replace(' ', ' ').replace(' ', ' ');
  }

  /** 12345 → « 12 345 » (espace insécable Latin-1). */
  static String money(long v) {
    String digits = Long.toString(Math.abs(v));
    StringBuilder sb = new StringBuilder();
    for (int k = 0; k < digits.length(); k++) {
      if (k > 0 && (digits.length() - k) % 3 == 0) sb.append(' ');
      sb.append(digits.charAt(k));
    }
    return (v < 0 ? "-" : "") + sb;
  }

  /** Largeur approchée en points (Helvetica ≈ 0,52 em, gras ≈ 0,56 em) : suffisant pour l'alignement à droite. */
  private static double width(String s, int size, boolean bold) {
    return s.length() * size * (bold ? 0.56 : 0.52);
  }

  private static void text(StringBuilder c, double x, double y, int size, boolean bold, String s) {
    c.append("BT /").append(bold ? "F2" : "F1").append(' ').append(size).append(" Tf ").append(String.format(Locale.ROOT, "%.1f %.1f", x, y)).append(" Td (").append(escape(s))
        .append(") Tj ET\n");
  }

  private static String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (char ch : s.toCharArray()) {
      if (ch == '(' || ch == ')' || ch == '\\') sb.append('\\').append(ch);
      else if (ch == '—') sb.append("\\227"); // tiret cadratin, WinAnsi 0x97
      else if (ch == '’') sb.append("\\222");
      else if (ch > 0xFF) sb.append('?');
      else if (ch < 0x20) sb.append(' ');
      else if (ch > 0x7E) sb.append('\\').append(String.format("%03o", (int) ch));
      else sb.append(ch);
    }
    return sb.toString();
  }

  private static byte[] document(String content) {
    byte[] stream = content.getBytes(StandardCharsets.US_ASCII);
    List<String> objects = new ArrayList<>();
    objects.add("<< /Type /Catalog /Pages 2 0 R >>");
    objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
    objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>");
    objects.add("<< /Length " + stream.length + " >>\nstream\n" + content + "endstream");
    objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
    objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
    objects.add("<< /Title (Facture BeepClick) /Producer (BeepClick) >>");
    StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
    List<Integer> offsets = new ArrayList<>();
    for (int n = 0; n < objects.size(); n++) {
      offsets.add(pdf.length());
      pdf.append(n + 1).append(" 0 obj\n").append(objects.get(n)).append("\nendobj\n");
    }
    int xref = pdf.length();
    pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
    for (int off : offsets) pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", off));
    pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R /Info 7 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
    return pdf.toString().getBytes(StandardCharsets.US_ASCII);
  }
}
