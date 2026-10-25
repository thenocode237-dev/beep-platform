package io.beep.core.app.link;

import io.beep.core.app.common.PublicSite;
import io.beep.core.app.port.CustomDomainRepository;
import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.port.PasswordHasherPort;
import io.beep.core.app.port.SlugRegistryPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.customdomain.CustomDomain;
import io.beep.core.domain.link.Destination;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.link.Slug;
import io.beep.core.domain.link.SlugGenerator;
import io.beep.core.domain.plan.QuotaPolicy;
import io.beep.core.domain.plan.QuotaPolicy.Resource;
import io.beep.core.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Création « en un champ » : destination obligatoire, slug personnalisé et titre optionnels.
 * Le contrôle de quota et l'insertion se font sous verrou du workspace : deux créations simultanées ne dépassent jamais le quota.
 */
public class CreateLinkUseCase {
  /**
   * @param domain null : domaine par défaut du workspace (sinon celui de Beep) ; « main » : domaine de Beep ; sinon identifiant d'un domaine vérifié du workspace
   */
  public record Command(String destination, String slug, String title, String domain, LinkRulesCommand rules) {
    public Command(String destination, String slug, String title, String domain) {
      this(destination, slug, title, domain, null);
    }

    public Command(String destination, String slug, String title) {
      this(destination, slug, title, null, null);
    }
  }

  private static final int GENERATION_ATTEMPTS = 5;

  private final Workspaces workspaces;
  private final WorkspaceRepository workspaceRepository;
  private final LinkRepository links;
  private final TransactionPort tx;
  private final PublicSite site;
  private final Clock clock;
  private final CustomDomainRepository domains;
  private PasswordHasherPort hasher;
  private io.beep.core.app.trust.TrustUseCase trust;
  public CreateLinkUseCase withTrust(io.beep.core.app.trust.TrustUseCase trust) { this.trust=trust;return this; }


  /** Active les règles de lien (mot de passe haché avec ce port). */
  public CreateLinkUseCase withHasher(PasswordHasherPort hasher) {
    this.hasher = hasher;
    return this;
  }

  public CreateLinkUseCase(Workspaces workspaces, WorkspaceRepository workspaceRepository, LinkRepository links, TransactionPort tx, PublicSite site, Clock clock) {
    this(workspaces, workspaceRepository, links, tx, site, clock, null);
  }

  /** `domains` : dépôt des domaines personnalisés ; null = fonction désactivée (tous les liens sur le domaine de Beep). */
  public CreateLinkUseCase(Workspaces workspaces, WorkspaceRepository workspaceRepository, LinkRepository links, TransactionPort tx, PublicSite site, Clock clock,
      CustomDomainRepository domains) {
    this.domains = domains;
    this.workspaces = workspaces;
    this.workspaceRepository = workspaceRepository;
    this.links = links;
    this.tx = tx;
    this.site = site;
    this.clock = clock;
  }

  public Link execute(User actor, Command cmd) {
    Destination destination = Destination.parse(cmd.destination());
    destination.requireNotHost(site.host());
    Slug custom = cmd.slug() == null || cmd.slug().isBlank() ? null : Slug.custom(cmd.slug());
    Workspaces.Context ctx = workspaces.context(actor);
    ctx.workspace().requireWritable();
    if (trust != null && cmd.rules() != null) trust.requireFlag("links.rules",ctx.id());
    Instant now = clock.instant();
    CustomDomain domain = chooseDomain(ctx.id(), cmd.domain());
    if (cmd.rules() != null && hasher == null) throw new DomainException("feature_unavailable");

    for (int attempt = 0; attempt < GENERATION_ATTEMPTS; attempt++) {
      Link link = Link.create(UUID.randomUUID(), ctx.id(), custom != null ? custom : SlugGenerator.next(), destination, cmd.title(), now, domain == null ? null : domain.id(),
          domain == null ? null : domain.hostname());
      try {
        Link withRules = cmd.rules() == null ? link : applyRules(link, cmd.rules(), ctx.entitlements(), now);
        tx.run(() -> {
          workspaceRepository.lock(ctx.id());
          QuotaPolicy.requireCreatable(ctx.entitlements(), Resource.LINKS, workspaces.usage(ctx.id(), Resource.LINKS));
          if (trust != null) io.beep.core.app.trust.LinkSafety.requireSafe(trust,withRules);
          links.insert(withRules);
          workspaceRepository.markFirstLink(ctx.id(), now);
        });
        return withRules;
      } catch (SlugRegistryPort.SlugTakenException taken) {
        if (custom != null) throw new DomainException("slug_taken");
        // slug généré déjà pris (improbable) : nouvel essai avec un autre slug
      }
    }
    throw new IllegalStateException("aucun slug libre après " + GENERATION_ATTEMPTS + " essais");
  }

  private Link applyRules(Link link, LinkRulesCommand cmd, io.beep.core.domain.plan.Entitlements entitlements, Instant now) {
    LinkRulesCommand.Applied a = cmd.apply(link, entitlements, hasher, now);
    return link.withRules(a.rules(), a.passwordHash(), now);
  }

  /** Domaine du nouveau lien : explicite, sinon le défaut du workspace. Il doit appartenir au workspace et être servable. */
  private CustomDomain chooseDomain(UUID workspaceId, String requested) {
    if (domains == null || "main".equals(requested)) return null;
    CustomDomain domain;
    if (requested == null || requested.isBlank()) {
      domain = domains.defaultOf(workspaceId).orElse(null);
      if (domain == null || !domain.servable()) return null;
      return domain;
    }
    UUID id;
    try {
      id = UUID.fromString(requested);
    } catch (IllegalArgumentException e) {
      throw new DomainException("invalid_request");
    }
    domain = domains.findById(id).filter(d -> d.workspaceId().equals(workspaceId)).orElseThrow(() -> new DomainException("not_found"));
    if (!domain.servable()) throw new DomainException("domain_not_verified");
    return domain;
  }
}
