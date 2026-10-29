package io.beep.core.adapter.out.qrrender;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import io.beep.core.app.port.QrRendererPort;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.qr.QrFormat;
import io.beep.core.domain.qr.QrStyle;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * La matrice vient de ZXing ; le dessin est fait ici, sans dépendance graphique lourde :
 * SVG (un seul chemin), PNG net (multiple entier de pixels par module), PDF 1.4 vectoriel prêt à imprimer.
 */
public class ZxingQrRendererAdapter implements QrRendererPort {
  @Override public void validateStyle(QrStyle style) {if(style.advanced()!=null) AdvancedQrRenderer.validate(style.advanced().logo());}
  @Override
  public byte[] render(String content, QrStyle style, QrFormat format, int sizePx) {
    ByteMatrix m = matrix(content, style);
    if(style.advanced()!=null) return AdvancedQrRenderer.render(m,style,format,sizePx);
    return switch (format) {
      case SVG -> svg(m, style, sizePx).getBytes(StandardCharsets.UTF_8);
      case PNG -> png(m, style, sizePx);
      case PDF -> pdf(m, style);
    };
  }

  static ByteMatrix matrix(String content, QrStyle style) {
    try {
      ErrorCorrectionLevel level = ErrorCorrectionLevel.valueOf(String.valueOf(style.errorCorrection()));
      return Encoder.encode(content, level, Map.of(EncodeHintType.CHARACTER_SET, "UTF-8")).getMatrix();
    } catch (WriterException | IllegalArgumentException e) {
      throw new DomainException("invalid_qr"); // contenu trop long pour un QR code
    }
  }

  static String svg(ByteMatrix m, QrStyle s, int sizePx) {
    int n = m.getWidth() + 2 * s.margin();
    StringBuilder path = new StringBuilder();
    for (int y = 0; y < m.getHeight(); y++) {
      for (int x = 0; x < m.getWidth(); x++) {
        if (m.get(x, y) == 1) path.append('M').append(x + s.margin()).append(' ').append(y + s.margin()).append("h1v1h-1z");
      }
    }
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + sizePx + "\" height=\"" + sizePx + "\" viewBox=\"0 0 " + n + " " + n
        + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR code\">"
        + "<rect width=\"" + n + "\" height=\"" + n + "\" fill=\"" + s.background() + "\"/>"
        + "<path fill=\"" + s.foreground() + "\" d=\"" + path + "\"/></svg>\n";
  }

  static byte[] png(ByteMatrix m, QrStyle s, int sizePx) {
    int n = m.getWidth() + 2 * s.margin();
    int scale = Math.max(1, sizePx / n);
    BufferedImage img = new BufferedImage(n * scale, n * scale, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    try {
      g.setColor(Color.decode(s.background()));
      g.fillRect(0, 0, img.getWidth(), img.getHeight());
      g.setColor(Color.decode(s.foreground()));
      for (int y = 0; y < m.getHeight(); y++) {
        for (int x = 0; x < m.getWidth(); x++) {
          if (m.get(x, y) == 1) g.fillRect((x + s.margin()) * scale, (y + s.margin()) * scale, scale, scale);
        }
      }
    } finally {
      g.dispose();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(img, "png", out);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /** PDF 1.4 d'une page carrée de 8 cm (226,77 pt), modules vectoriels : net à toute taille d'impression. */
  static byte[] pdf(ByteMatrix m, QrStyle s) {
    int n = m.getWidth() + 2 * s.margin();
    double page = 226.77;
    double unit = page / n;
    StringBuilder c = new StringBuilder();
    c.append(rgb(s.background())).append(" rg 0 0 ").append(fmt(page)).append(' ').append(fmt(page)).append(" re f\n");
    c.append(rgb(s.foreground())).append(" rg\n");
    for (int y = 0; y < m.getHeight(); y++) {
      for (int x = 0; x < m.getWidth(); x++) {
        if (m.get(x, y) != 1) continue;
        double px = (x + s.margin()) * unit;
        double py = page - (y + s.margin() + 1) * unit; // origine PDF en bas à gauche
        c.append(fmt(px)).append(' ').append(fmt(py)).append(' ').append(fmt(unit)).append(' ').append(fmt(unit)).append(" re\n");
      }
    }
    c.append("f\n");
    byte[] stream = c.toString().getBytes(StandardCharsets.US_ASCII);

    List<String> objects = new ArrayList<>();
    objects.add("<< /Type /Catalog /Pages 2 0 R >>");
    objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
    objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + fmt(page) + " " + fmt(page) + "] /Contents 4 0 R /Resources << >> >>");
    objects.add("<< /Length " + stream.length + " >>\nstream\n" + new String(stream, StandardCharsets.US_ASCII) + "endstream");
    objects.add("<< /Title (QR code BeepClick) /Producer (BeepClick) >>");

    StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
    List<Integer> offsets = new ArrayList<>();
    for (int i = 0; i < objects.size(); i++) {
      offsets.add(pdf.length());
      pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
    }
    int xref = pdf.length();
    pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
    for (int off : offsets) pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", off));
    pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R /Info 5 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
    return pdf.toString().getBytes(StandardCharsets.US_ASCII);
  }

  private static String rgb(String hex) {
    int v = Integer.parseInt(hex.substring(1), 16);
    return fmt(((v >> 16) & 0xFF) / 255.0) + " " + fmt(((v >> 8) & 0xFF) / 255.0) + " " + fmt((v & 0xFF) / 255.0);
  }

  private static String fmt(double d) {
    return String.format(Locale.ROOT, "%.3f", d);
  }
}
