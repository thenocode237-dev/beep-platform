package io.beep.core.domain.customdomain;

import io.beep.core.domain.DomainException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Domaine personnalisé d'un workspace. Il sert des liens uniquement une fois le DNS vérifié (propriété par TXT, routage par CNAME/A),
 * tant qu'il n'est pas bloqué par le staff et que le workspace n'est pas suspendu.
 *
 * @param rootRedirectUrl page racine : redirection de `domaine/` (sinon la page 404 de BeepClick)
 * @param notFoundUrl     redirection d'un chemin inconnu (sinon la page 404 de BeepClick)
 */
public record CustomDomain(UUID id, UUID workspaceId, String hostname, DnsStatus dnsStatus, TlsStatus tlsStatus, String verifyToken, Instant verifiedAt, Instant lastCheckAt,
    String lastError, boolean isDefault, String rootRedirectUrl, String notFoundUrl, boolean blocked, String blockedReason, Instant createdAt) {
  public static final String TXT_PREFIX = "_beep-verify.";

  public static CustomDomain pending(UUID id, UUID workspaceId, String hostname, String token, Instant now) {
    return new CustomDomain(id, workspaceId, hostname, DnsStatus.PENDING, TlsStatus.PENDING, token, null, null, null, false, null, null, false, null, now);
  }

  /** Enregistrement TXT à créer pour prouver la propriété. */
  public String txtName() {
    return TXT_PREFIX + hostname;
  }

  public boolean verified() {
    return dnsStatus == DnsStatus.VERIFIED;
  }

  /** Peut servir des liens (hors suspension du workspace, contrôlée à part). */
  public boolean servable() {
    return verified() && !blocked;
  }

  public CustomDomain checked(boolean ok, String error, Instant now) {
    if (ok) {
      return new CustomDomain(id, workspaceId, hostname, DnsStatus.VERIFIED, tlsStatus, verifyToken, verifiedAt != null ? verifiedAt : now, now, null, isDefault, rootRedirectUrl,
          notFoundUrl, blocked, blockedReason, createdAt);
    }
    // Un domaine déjà vérifié dont le DNS casse reste servi (une panne DNS passagère ne doit pas couper les liens) ; l'erreur est signalée.
    return new CustomDomain(id, workspaceId, hostname, verified() ? DnsStatus.VERIFIED : DnsStatus.FAILED, tlsStatus, verifyToken, verifiedAt, now, error, isDefault,
        rootRedirectUrl, notFoundUrl, blocked, blockedReason, createdAt);
  }

  public CustomDomain withTlsActive() {
    return new CustomDomain(id, workspaceId, hostname, dnsStatus, TlsStatus.ACTIVE, verifyToken, verifiedAt, lastCheckAt, lastError, isDefault, rootRedirectUrl, notFoundUrl, blocked,
        blockedReason, createdAt);
  }

  public CustomDomain withDefault(boolean value) {
    if (value && !servable()) throw new DomainException("domain_not_verified");
    return new CustomDomain(id, workspaceId, hostname, dnsStatus, tlsStatus, verifyToken, verifiedAt, lastCheckAt, lastError, value, rootRedirectUrl, notFoundUrl, blocked,
        blockedReason, createdAt);
  }

  public CustomDomain withRedirects(String root, String notFound) {
    return new CustomDomain(id, workspaceId, hostname, dnsStatus, tlsStatus, verifyToken, verifiedAt, lastCheckAt, lastError, isDefault, redirectUrl(root), redirectUrl(notFound), blocked,
        blockedReason, createdAt);
  }

  public CustomDomain block(String reason) {
    return new CustomDomain(id, workspaceId, hostname, dnsStatus, tlsStatus, verifyToken, verifiedAt, lastCheckAt, lastError, false, rootRedirectUrl, notFoundUrl, true,
        io.beep.core.domain.link.Link.reason(reason), createdAt);
  }

  public CustomDomain unblock() {
    return new CustomDomain(id, workspaceId, hostname, dnsStatus, tlsStatus, verifyToken, verifiedAt, lastCheckAt, lastError, isDefault, rootRedirectUrl, notFoundUrl, false, null,
        createdAt);
  }

  /** Redirection configurable : vide = aucune ; sinon URL https:// (ou http://) vers un autre hôte, jamais le domaine lui-même. */
  String redirectUrl(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String url = raw.strip();
    if (url.length() > 2000) throw new DomainException("invalid_destination");
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new DomainException("invalid_destination");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
    if ((!scheme.equals("https") && !scheme.equals("http")) || uri.getHost() == null) throw new DomainException("invalid_destination");
    if (uri.getHost().equalsIgnoreCase(hostname)) throw new DomainException("destination_loop");
    return url;
  }
}
