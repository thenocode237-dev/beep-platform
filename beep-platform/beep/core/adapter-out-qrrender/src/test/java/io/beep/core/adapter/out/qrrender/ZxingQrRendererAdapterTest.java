package io.beep.core.adapter.out.qrrender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.beep.core.domain.qr.QrFormat;
import io.beep.core.domain.qr.QrStyle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ZxingQrRendererAdapterTest {
  final ZxingQrRendererAdapter renderer = new ZxingQrRendererAdapter();
  final QrStyle style = QrStyle.of("#243D30", "#FFFFFF", "M", 2);

  @Test
  void svgUsesTheStyleColorsAndASinglePath() {
    String svg = new String(renderer.render("https://beep.click/q/Ab3dEf9h", style, QrFormat.SVG, 512), StandardCharsets.UTF_8);
    assertThat(svg).startsWith("<?xml").contains("width=\"512\"").contains("fill=\"#243D30\"").contains("fill=\"#FFFFFF\"");
    assertThat(svg.split("<path").length).isEqualTo(2);
  }

  @Test
  void pngIsSharpWithAnIntegerNumberOfPixelsPerModule() throws Exception {
    BufferedImage img = ImageIO.read(new ByteArrayInputStream(renderer.render("https://beep.click/q/Ab3dEf9h", style, QrFormat.PNG, 1024)));
    int modules = ZxingQrRendererAdapter.matrix("https://beep.click/q/Ab3dEf9h", style).getWidth() + 4;
    assertThat(img.getWidth()).isEqualTo(img.getHeight()).isEqualTo(modules * (1024 / modules));
    assertThat(img.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xFFFFFF); // marge claire
  }

  @Test
  void pdfIsAWellFormedSinglePageDocument() {
    String pdf = new String(renderer.render("WIFI:T:WPA;S:Atelier;P:secret;;", style, QrFormat.PDF, 0), StandardCharsets.US_ASCII);
    assertThat(pdf).startsWith("%PDF-1.4").endsWith("%%EOF\n").contains("/Type /Page ").contains(" re\n");
    int xref = Integer.parseInt(pdf.substring(pdf.indexOf("startxref\n") + 10, pdf.indexOf("\n%%EOF")).trim());
    assertThat(pdf.substring(xref)).startsWith("xref");
  }

  @Test
  void contentTooLongForAQrCodeIsRefused() {
    assertThatThrownBy(() -> renderer.render("x".repeat(5000), style, QrFormat.SVG, 256)).hasMessage("invalid_qr");
  }
}
