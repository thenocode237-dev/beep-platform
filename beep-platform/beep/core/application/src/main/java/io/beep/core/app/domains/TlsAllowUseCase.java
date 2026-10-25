package io.beep.core.app.domains;

import io.beep.core.app.port.CustomDomainRepository;
import io.beep.core.app.port.DomainRouterPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.domain.customdomain.TlsStatus;
import java.util.Locale;

/**
 * Point `ask` de Caddy (TLS à la demande) : un certificat n'est émis que pour un domaine personnalisé vérifié, non bloqué, dont le workspace est actif.
 * La première autorisation passe l'état TLS à « actif » (Caddy émet à la première visite HTTPS).
 */
public class TlsAllowUseCase {
  private final DomainRouterPort router;
  private final CustomDomainRepository domains;
  private final TransactionPort tx;

  public TlsAllowUseCase(DomainRouterPort router, CustomDomainRepository domains, TransactionPort tx) {
    this.router = router;
    this.domains = domains;
    this.tx = tx;
  }

  public boolean allowed(String host) {
    if (host == null || host.isBlank() || host.length() > 253) return false;
    String h = host.strip().toLowerCase(Locale.ROOT);
    DomainRouterPort.Route route = router.route(h);
    if (route.kind() != DomainRouterPort.Route.Kind.CUSTOM || !route.servable()) return false;
    domains.findByHostname(h).filter(d -> d.tlsStatus() == TlsStatus.PENDING).ifPresent(d -> tx.run(() -> domains.update(d.withTlsActive())));
    return true;
  }
}
