package io.beep.core.domain.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QrDomainTest {
  @Test
  void encodesStaticPayloadsInFormatsReadersUnderstand() {
    assertThat(QrContent.of(QrContent.Type.WIFI, Map.of("ssid", "Atelier;Naya", "password", "p:ss", "security", "wpa")).encode())
        .isEqualTo("WIFI:T:WPA;S:Atelier\\;Naya;P:p\\:ss;;");
    assertThat(QrContent.of(QrContent.Type.WIFI, Map.of("ssid", "Libre", "security", "nopass")).encode()).isEqualTo("WIFI:T:nopass;S:Libre;;");
    assertThat(QrContent.of(QrContent.Type.EMAIL, Map.of("to", "Hello@Example.com", "subject", "Bonjour à vous")).encode())
        .isEqualTo("mailto:hello@example.com?subject=Bonjour%20%C3%A0%20vous");
    assertThat(QrContent.of(QrContent.Type.PHONE, Map.of("phone", "+237 6 99 00 11 22")).encode()).isEqualTo("tel:+237699001122");
    assertThat(QrContent.of(QrContent.Type.VCARD, Map.of("name", "Naya, Atelier", "phone", "+237699001122")).encode())
        .contains("FN:Naya\\, Atelier").startsWith("BEGIN:VCARD").endsWith("END:VCARD");
    assertThat(QrContent.of(QrContent.Type.URL, Map.of("url", "example.com")).encode()).isEqualTo("https://example.com");
  }

  @Test
  void rejectsUnknownFieldsAndMissingRequiredOnes() {
    assertThatThrownBy(() -> QrContent.of(QrContent.Type.TEXT, Map.of("txt", "x"))).hasMessage("invalid_qr");
    assertThatThrownBy(() -> QrContent.of(QrContent.Type.WIFI, Map.of("ssid", "x", "security", "WPA"))).hasMessage("invalid_qr");
    assertThatThrownBy(() -> QrContent.of(QrContent.Type.SMS, Map.of("phone", "abc"))).hasMessage("invalid_qr");
    assertThatThrownBy(() -> QrContent.of(QrContent.Type.TEXT, Map.of("text", "x".repeat(1001)))).hasMessage("invalid_qr");
  }

  @Test
  void styleRequiresDarkModulesOnALightBackground() {
    assertThat(QrStyle.of("#bd3516", null, "h", 4).foreground()).isEqualTo("#BD3516");
    assertThatThrownBy(() -> QrStyle.of("#FFFFFF", "#000000", "M", 2)).hasMessage("qr_low_contrast"); // inversé
    assertThatThrownBy(() -> QrStyle.of("#DDDDDD", "#FFFFFF", "M", 2)).hasMessage("qr_low_contrast"); // trop pâle
    assertThatThrownBy(() -> QrStyle.of("red", null, null, null)).hasMessage("invalid_qr");
    assertThatThrownBy(() -> QrStyle.of(null, null, "X", null)).hasMessage("invalid_qr");
  }

  @Test
  void dynamicCodesEncodeTheirShortUrlAndStaticCodesTheirContent() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    QrCode dyn = QrCode.dynamic(UUID.randomUUID(), UUID.randomUUID(), "Ab3dEf9h", UUID.randomUUID(), QrStyle.DEFAULT, null, now);
    assertThat(dyn.encodedValue("https://beepclick.app/")).isEqualTo("https://beepclick.app/q/Ab3dEf9h");
    QrCode fixed = QrCode.fixed(UUID.randomUUID(), UUID.randomUUID(), QrContent.of(QrContent.Type.TEXT, Map.of("text", "Bonjour")), QrStyle.DEFAULT, "t", now);
    assertThat(fixed.encodedValue("https://beepclick.app")).isEqualTo("Bonjour");
    assertThatThrownBy(() -> dyn.edit(null, null, QrStyle.DEFAULT, null, now)).hasMessage("invalid_qr");
  }
}
