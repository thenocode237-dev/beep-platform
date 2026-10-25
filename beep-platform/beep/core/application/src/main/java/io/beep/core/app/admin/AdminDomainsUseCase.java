package io.beep.core.app.admin;

import io.beep.core.app.common.Paging;
import io.beep.core.app.domains.CustomDomainsUseCase;
import io.beep.core.app.port.CustomDomainRepository;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.customdomain.CustomDomain;
import io.beep.core.domain.link.Link;
import java.util.UUID;

/** Domaines côté staff : liste globale, état DNS/TLS, relance de la vérification, blocage et déblocage d'un domaine. */
public class AdminDomainsUseCase {
  private final CustomDomainRepository domains;
  private final CustomDomainsUseCase customer;
  private final TransactionPort tx;

  public AdminDomainsUseCase(CustomDomainRepository domains, CustomDomainsUseCase customer, TransactionPort tx) {
    this.domains = domains;
    this.customer = customer;
    this.tx = tx;
  }

  public CustomDomainRepository.AdminPage page(String query, String status, int limit, String cursor) {
    String s = status == null || status.isBlank() ? null : status;
    if (s != null && !java.util.Set.of("pending", "verified", "failed", "blocked").contains(s)) throw new DomainException("invalid_request");
    return domains.adminPage(Paging.query(query), s, Paging.limit(limit), cursor);
  }

  public CustomDomainRepository.AdminRow get(UUID id) {
    return domains.adminFind(id).orElseThrow(() -> new DomainException("not_found"));
  }

  public CustomDomainRepository.AdminRow recheck(UUID id) {
    CustomDomain d = domains.findById(id).orElseThrow(() -> new DomainException("not_found"));
    customer.recheck(d);
    return get(id);
  }

  /** Motif obligatoire (journalisé par le back office). Un domaine bloqué cesse de servir ses liens et ne reçoit plus de certificat. */
  public CustomDomainRepository.AdminRow block(UUID id, String reason) {
    String why = Link.reason(reason);
    tx.run(() -> domains.update(domains.lockById(id).orElseThrow(() -> new DomainException("not_found")).block(why)));
    return get(id);
  }

  public CustomDomainRepository.AdminRow unblock(UUID id) {
    tx.run(() -> domains.update(domains.lockById(id).orElseThrow(() -> new DomainException("not_found")).unblock()));
    return get(id);
  }
}
