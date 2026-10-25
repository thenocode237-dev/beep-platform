package io.beep.core.app.port;

import io.beep.core.domain.qr.QrFormat;
import io.beep.core.domain.qr.QrStyle;

/** Rendu d'un QR code : vectoriel (SVG, PDF) ou matriciel haute résolution (PNG). */
public interface QrRendererPort {
  default void validateStyle(io.beep.core.domain.qr.QrStyle style) {}
  byte[] render(String content, QrStyle style, QrFormat format, int sizePx);
}
