package io.beep.core.domain.qr;

import io.beep.core.domain.DomainException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * QR code. Dynamique : encode `{public}/q/{code}` et suit un lien Beep (destination modifiable sans réimprimer,
 * scans mesurés). Statique : encode directement son contenu.
 */
public record QrCode(
    UUID id,
    UUID workspaceId,
    Kind kind,
    String code,
    UUID linkId,
    QrContent content,
    QrStyle style,
    String title,
    Instant createdAt,
    Instant updatedAt) {
  public static final int CODE_LENGTH = 8;
  public static final int MAX_TITLE = 200;

  public enum Kind {
    DYNAMIC,
    STATIC;

    public String db() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Kind fromDb(String v) {
      return valueOf(v.toUpperCase(Locale.ROOT));
    }
  }

  public static QrCode dynamic(UUID id, UUID workspaceId, String code, UUID linkId, QrStyle style, String title, Instant now) {
    if (linkId == null || code == null) throw new DomainException("invalid_qr");
    return new QrCode(id, workspaceId, Kind.DYNAMIC, code, linkId, null, style, title(title), now, now);
  }

  public static QrCode fixed(UUID id, UUID workspaceId, QrContent content, QrStyle style, String title, Instant now) {
    if (content == null) throw new DomainException("invalid_qr");
    return new QrCode(id, workspaceId, Kind.STATIC, null, null, content, style, title(title), now, now);
  }

  public boolean dynamic() {
    return kind == Kind.DYNAMIC;
  }

  /** Données encodées dans l'image. */
  public String encodedValue(String publicUrl) {
    return dynamic() ? publicUrl.replaceAll("/+$", "") + "/q/" + code : content.encode();
  }

  /** Un QR dynamique peut changer de lien cible ; un QR statique, de contenu (avant impression). */
  public QrCode edit(UUID newLinkId, QrContent newContent, QrStyle newStyle, String newTitle, Instant now) {
    if (dynamic() && newLinkId == null || !dynamic() && newContent == null) throw new DomainException("invalid_qr");
    return new QrCode(id, workspaceId, kind, code, dynamic() ? newLinkId : null, dynamic() ? null : newContent, newStyle, title(newTitle), createdAt, now);
  }

  static String title(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String t = raw.strip();
    if (t.length() > MAX_TITLE) throw new DomainException("invalid_title");
    return t;
  }
}
