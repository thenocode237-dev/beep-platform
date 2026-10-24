package io.beep.core.app.workspace;

import io.beep.core.app.port.BioPageRepository;
import io.beep.core.app.port.LinkRepository;
import io.beep.core.app.port.PlanRepository;
import io.beep.core.app.port.QrCodeRepository;
import io.beep.core.app.port.WorkspaceRepository;
import io.beep.core.domain.plan.Entitlements;
import io.beep.core.domain.plan.Plan;
import io.beep.core.domain.plan.QuotaPolicy;
import io.beep.core.domain.plan.QuotaPolicy.Resource;
import io.beep.core.domain.user.User;
import io.beep.core.domain.workspace.Workspace;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Accès au workspace d'un utilisateur, à ses droits effectifs et à son usage. Partagé par les cas d'usage.
 * Le workspace personnel est créé à la première utilisation (comptes antérieurs à la phase 1 compris).
 */
public class Workspaces {
  public record Context(Workspace workspace, Entitlements entitlements) {
    public UUID id() {
      return workspace.id();
    }
  }

  private final WorkspaceRepository workspaces;
  private final PlanRepository plans;
  private final LinkRepository links;
  private final QrCodeRepository qrCodes;
  private final BioPageRepository bioPages;
  private final Clock clock;

  public Workspaces(WorkspaceRepository workspaces, PlanRepository plans, LinkRepository links, QrCodeRepository qrCodes, BioPageRepository bioPages, Clock clock) {
    this.workspaces = workspaces;
    this.plans = plans;
    this.links = links;
    this.qrCodes = qrCodes;
    this.bioPages = bioPages;
    this.clock = clock;
  }

  public Workspace ensureFor(User user) {
    return workspaces.findByOwner(user.id())
        .orElseGet(() -> workspaces.ensure(Workspace.personal(UUID.randomUUID(), user.id(), defaultName(user), clock.instant())));
  }

  public Context context(User user) {
    Workspace w = ensureFor(user);
    return new Context(w, entitlements(w));
  }

  public Entitlements entitlements(Workspace w) {
    Plan plan = plans.findByCode(w.planCode()).orElseThrow(() -> new IllegalStateException("plan inconnu : " + w.planCode()));
    Map<String, Long> overrides = plans.overrides(w.id()).stream().collect(Collectors.toMap(PlanRepository.EntitlementOverride::key, PlanRepository.EntitlementOverride::value));
    return Entitlements.of(plan, overrides);
  }

  public QuotaPolicy.Usage usage(UUID workspaceId, Resource resource) {
    Instant start = periodStart();
    return switch (resource) {
      case LINKS -> new QuotaPolicy.Usage(links.count(workspaceId), links.countCreatedSince(workspaceId, start));
      case QR -> new QuotaPolicy.Usage(qrCodes.countDynamic(workspaceId), qrCodes.countDynamicCreatedSince(workspaceId, start));
      case BIO -> new QuotaPolicy.Usage(bioPages.count(workspaceId), 0);
    };
  }

  public List<QuotaPolicy.Status> quotas(UUID workspaceId, Entitlements entitlements) {
    return Arrays.stream(Resource.values()).map(r -> QuotaPolicy.status(entitlements, r, usage(workspaceId, r))).toList();
  }

  /** Période des quotas mensuels : mois civil UTC (les cycles de facturation arrivent avec les abonnements, phase 2). */
  public Instant periodStart() {
    return LocalDate.now(clock.withZone(ZoneOffset.UTC)).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  private static String defaultName(User user) {
    String local = user.email().value().split("@", 2)[0];
    return local.length() > 60 ? local.substring(0, 60) : local;
  }
}
