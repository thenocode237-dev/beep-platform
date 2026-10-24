package io.beep.core.app.analytics;

import io.beep.core.app.port.ClickSinkPort;
import io.beep.core.app.port.GeoIpPort;
import io.beep.core.app.port.VisitorSaltRepository;
import io.beep.core.domain.analytics.ClickEvent;
import io.beep.core.domain.analytics.EventKind;
import io.beep.core.domain.analytics.UserAgent;
import io.beep.core.domain.analytics.VisitSignals;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Transforme une visite en événement pseudonymisé et le confie à la file d'écriture.
 * L'IP sert uniquement au pays (base locale) et à l'empreinte salée du jour ; elle n'est jamais stockée.
 * Ne lève jamais d'exception : la mesure ne doit pas pouvoir empêcher une redirection.
 */
public class ClickRecorder {
  private final ClickSinkPort sink;
  private final VisitorSaltRepository salts;
  private final GeoIpPort geoIp;

  public ClickRecorder(ClickSinkPort sink, VisitorSaltRepository salts, GeoIpPort geoIp) {
    this.sink = sink;
    this.salts = salts;
    this.geoIp = geoIp;
  }

  public record Target(UUID workspaceId, EventKind kind, UUID linkId, UUID qrId, UUID bioPageId, String bioBlockId) {}

  public boolean record(Visit visit, Target target) {
    if (!visit.countable()) return false;
    try {
      UserAgent ua = UserAgent.parse(visit.userAgent());
      LocalDate day = LocalDate.ofInstant(visit.at(), ZoneOffset.UTC);
      String hash = visitorHash(salts.saltFor(day), visit.ip(), visit.userAgent());
      return sink.offer(new ClickEvent(UUID.randomUUID(), visit.at(), target.workspaceId(), target.kind(), target.linkId(), target.qrId(), target.bioPageId(),
          target.bioBlockId(), hash, ua.bot() ? null : geoIp.country(visit.ip()), ua.device(), ua.os(), ua.browser(),
          VisitSignals.primaryLanguage(visit.acceptLanguage()), VisitSignals.referrerHost(visit.referer()), ua.bot()));
    } catch (RuntimeException e) {
      return false;
    }
  }

  /** SHA-256(sel du jour ‖ IP ‖ User-Agent), tronqué à 128 bits : assez pour compter, inutilisable sans le sel. */
  static String visitorHash(byte[] salt, String ip, String userAgent) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(salt);
      md.update(String.valueOf(ip).getBytes(StandardCharsets.UTF_8));
      md.update((byte) 0x1f);
      md.update(String.valueOf(userAgent).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(md.digest(), 0, 16);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
